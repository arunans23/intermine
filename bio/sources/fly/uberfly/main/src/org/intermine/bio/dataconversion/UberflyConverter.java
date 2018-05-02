package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2017 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.tools.ant.BuildException;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;


/**
 * Converter to parse modENCODE expression data.
 *
 * @author Julie Sullivan
 */
public class UberflyConverter extends BioFileConverter
{
    private static final Logger LOG = Logger.getLogger(UberflyConverter.class);
    private static final String DATASET_TITLE = "Uberfly expression data";
    private static final String DATA_SOURCE_NAME = "Uberfly";
    private Item organism, flyDevelopmentOntology, flyAnatomyOntology;
    private static final String TAXON_FLY = "7227";
    private Map<String, String> genes = new HashMap<String, String>();
    private Map<String, Item> libraries = new HashMap<String, Item>();
    private Map<String, String> stages = new HashMap<String, String>();
    private Map<String, String> tissues = new HashMap<String, String>();
    protected IdResolver rslv;
    private File metadataFile;

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public UberflyConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);

        flyDevelopmentOntology = createItem("Ontology");
        flyDevelopmentOntology.setAttribute("name", "Fly Development");

        flyAnatomyOntology = createItem("Ontology");
        flyAnatomyOntology.setAttribute("name", "Fly Anatomy");

        organism = createItem("Organism");
        organism.setAttribute("taxonId", TAXON_FLY);

        try {
            store(organism);
        } catch (ObjectStoreException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param metadataFile data file with all the experiment info
     */
    public void setUberflyMetadataFile(File metadataFile) {
        this.metadataFile = metadataFile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(Reader reader) throws Exception {
        if (rslv == null) {
            rslv = IdResolverService.getFlyIdResolver();
        }
        processMetadataFile(new FileReader(metadataFile));
        processGeneFile(reader);
        for (Item item : libraries.values()) {
            store(item);
        }
    }

    private void processGeneFile(Reader reader) throws ObjectStoreException {
        Iterator<?> tsvIter;
        try {
            tsvIter = FormattedTextParser.parseTabDelimitedReader(reader);
        } catch (Exception e) {
            throw new BuildException("cannot parse file: " + getCurrentFile(), e);
        }

        String[] header = (String[]) tsvIter.next();

        while (tsvIter.hasNext()) {
            String[] line = (String[]) tsvIter.next();

            if (line.length < 4000) {
                LOG.error("Couldn't process line.  Expected 4000+ cols, but was " + line.length);
                continue;
            }

            // FBgn0000003
            final String fbgn = line[0];

            for (int i = 1; i < line.length; i++) {
                Item result = createItem("UberFlyRNASeqResult");
                result.setAttribute("count", line[i]);
                result.setReference("library", getLibrary(header[i]));
                String gene = getGene(fbgn);
                if (StringUtils.isNotEmpty(gene)) {
                    result.setReference("gene", gene);
                    store(result);
                }
            }
        }
    }

    private void processHeader(String[] header) throws ObjectStoreException {
        for (int i = 1; i < header.length; i++) {
            getLibrary(header[i]);
        }
    }

    private void processMetadataFile(Reader reader) throws ObjectStoreException {
        Iterator<?> tsvIter;
        try {
            tsvIter = FormattedTextParser.parseTabDelimitedReader(reader);
        } catch (Exception e) {
            throw new BuildException("cannot parse file: " + getCurrentFile(), e);
        }

        while (tsvIter.hasNext()) {
            String[] line = (String[]) tsvIter.next();

            String libraryIdentifier = line[0].trim();
            String sample = line[1].trim();
            String age = line[2].trim();
            String stage = line[3].trim();
            String name = line[4].trim();
            String sex = line[5].trim();
            String tissue = line[6].trim();

            Item library = getLibrary(libraryIdentifier);

            if (StringUtils.isNotEmpty(sample)) {
                library.setAttribute("sample", sample);
            }
            if (StringUtils.isNotEmpty(age)) {
                library.setAttribute("age", age);
            }
            if (StringUtils.isNotEmpty(stage)) {
                library.setReference("stage", getStage(stage));
            }
            if (StringUtils.isNotEmpty(name)) {
                library.setAttribute("name", name);
            }
            if (StringUtils.isNotEmpty(sex)) {
                library.setAttribute("sex", sex);
            }
            if (StringUtils.isNotEmpty(tissue)) {
                library.setReference("tissue", getTissue(tissue));
            }

            store(library);
        }
    }

    private String getTissue(String name) throws ObjectStoreException {
        if (tissues.containsKey(name)) {
            return tissues.get(name);
        }
        Item item = createItem("OntologyTerm");
        item.setAttribute("name", name);
        item.setReference("ontology", flyAnatomyOntology);
        String refId = item.getIdentifier();
        tissues.put(name, refId);
        store(item);
        return refId;
    }


    private String getStage(String name) throws ObjectStoreException {
        if (stages.containsKey(name)) {
            return stages.get(name);
        }
        Item item = createItem("OntologyTerm");
        item.setAttribute("name", name);
        item.setReference("ontology", flyDevelopmentOntology);
        String refId = item.getIdentifier();
        stages.put(name, refId);
        store(item);
        return refId;
    }

    private String getGene(String fbgn) throws ObjectStoreException {
        String identifier = resolveGene(fbgn);
        if (StringUtils.isEmpty(identifier)) {
            return null;
        }
        if (genes.containsKey(identifier)) {
            return genes.get(identifier);
        }
        Item gene = createItem("Gene");
        gene.setAttribute("primaryIdentifier", identifier);
        gene.setReference("organism", organism);
        String refId = gene.getIdentifier();
        genes.put(identifier, refId);
        store(gene);
        return refId;
    }

    private Item getLibrary(String identifier) throws ObjectStoreException {
        if (libraries.containsKey(identifier)) {
            return libraries.get(identifier);
        }
        Item item = createItem("UberFlyLibrary");
        item.setAttribute("identifier", identifier);
        libraries.put(identifier, item);
        return item;
    }

    private String resolveGene(String fbgn) {
        // if resolver not exist, return the original id as primary id
        if (rslv == null || !rslv.hasTaxon(TAXON_FLY)) {
            return fbgn;
        }
        boolean currentGene = rslv.isPrimaryIdentifier(TAXON_FLY, fbgn);
        if (currentGene) {
            return fbgn;
        }
        return null;
    }
}
