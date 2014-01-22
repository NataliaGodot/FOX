package org.aksw.fox.nerlearner.reader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.aksw.fox.data.Entity;
import org.aksw.fox.data.EntityClassMap;
import org.aksw.fox.data.TokenManager;
import org.aksw.fox.utils.FoxTextUtil;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/**
 * reads input training data
 * 
 * @author rspeck
 * 
 */
public class TrainingInputReader {

    public static Logger logger = Logger.getLogger(TrainingInputReader.class);

    /**
     * 
     */
    public static void main(String[] aa) throws Exception {
        PropertyConfigurator.configure("log4j.properties");

        String[] a = { "input/2/2" };

        TrainingInputReader trainingInputReader = new TrainingInputReader(a);
        TrainingInputReader.logger.info("input: ");
        TrainingInputReader.logger.info(trainingInputReader.getInput());
        TrainingInputReader.logger.info("oracle: ");
        for (Entry<String, String> e : trainingInputReader.getEntities().entrySet()) {
            TrainingInputReader.logger.info(e.getValue() + "-" + e.getKey());
        }
    }

    protected File[] inputFiles;
    protected StringBuffer taggedInput = new StringBuffer();
    protected String input = "";
    protected HashMap<String, String> entities = new HashMap<>();

    /**
     * http://www-nlpir.nist.gov/related_projects/muc/proceedings/ne_task.html
     * 
     * @param inputPaths
     * @throws IOException
     */
    public TrainingInputReader(String[] inputPaths) throws IOException {
        if (logger.isDebugEnabled())
            logger.debug("TrainingInputReader ...");

        inputFiles = new File[inputPaths.length];

        if (logger.isDebugEnabled())
            logger.debug("search files ...");

        for (int i = 0; i < inputPaths.length; i++) {
            inputFiles[i] = new File(inputPaths[i]);
            if (!inputFiles[i].exists())
                throw new FileNotFoundException(inputPaths[i]);
        }

        readInputFromFiles();
        parse();
    }

    /**
     * 
     * @return
     * @throws IOException
     */
    public String getInput() throws IOException {
        {
            // DEBUG
            if (logger.isDebugEnabled())
                logger.debug("getInput ...\n" + input);

            // INFO
            logger.info("input length: " + input.length());
        }

        return input;
    }

    public HashMap<String, String> getEntities() throws IOException {
        {
            // DEBUG
            if (logger.isDebugEnabled()) {
                logger.debug("getEntities ...");
                for (Entry<String, String> e : entities.entrySet())
                    logger.debug(e.getKey() + " -> " + e.getValue());
            }
            // INFO
            logger.info("oracle raw size: " + entities.size());
        }

        {
            // remove oracle entities aren't in input
            Set<Entity> set = new HashSet<>();

            for (Entry<String, String> oracleEntry : entities.entrySet())
                set.add(new Entity(oracleEntry.getKey(), oracleEntry.getValue()));

            // repair entities (use fox token)
            TokenManager tokenManager = new TokenManager(input);
            tokenManager.repairEntities(set);

            // use
            entities.clear();
            for (Entity e : set)
                entities.put(e.getText(), e.getType());
        }

        {
            // INFO
            logger.info("oracle cleaned size: " + entities.size());
            int l = 0, o = 0, p = 0;
            for (Entry<String, String> e : entities.entrySet()) {
                if (e.getValue().equals(EntityClassMap.L))
                    l++;
                if (e.getValue().equals(EntityClassMap.O))
                    o++;
                if (e.getValue().equals(EntityClassMap.P))
                    p++;
            }
            logger.info("oracle :");
            logger.info(l + " LOCs found");
            logger.info(o + " ORGs found");
            logger.info(p + " PERs found");

            l = 0;
            o = 0;
            p = 0;
            for (Entry<String, String> e : entities.entrySet()) {
                if (e.getValue().equals(EntityClassMap.L))
                    l += e.getKey().split(" ").length;
                if (e.getValue().equals(EntityClassMap.O))
                    o += e.getKey().split(" ").length;
                if (e.getValue().equals(EntityClassMap.P))
                    p += e.getKey().split(" ").length;
            }
            logger.info("oracle (token):");
            logger.info(l + " LOCs found");
            logger.info(o + " ORGs found");
            logger.info(p + " PERs found");
            logger.info(l + o + p + " total found");
        }

        return entities;
    }

    /**
     * Reads PREAMBLE or TEXT tag content to taggedInput.
     * 
     **/
    protected void readInputFromFiles() throws IOException {
        if (logger.isDebugEnabled())
            logger.debug("readInputFromFiles ...");

        for (File file : inputFiles) {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line = "";
            boolean includeLine = false;
            while ((line = br.readLine()) != null) {
                // open
                if (line.contains("<PREAMBLE>")) {
                    includeLine = true;
                    line = line.substring(line.indexOf("<PREAMBLE>") + "<PREAMBLE>".length());
                } else if (line.contains("<TEXT>")) {
                    includeLine = true;
                    line = line.substring(line.indexOf("<TEXT>") + "<TEXT>".length());
                }
                // close
                if (includeLine) {
                    if (line.contains("</PREAMBLE>")) {
                        includeLine = false;
                        if (line.indexOf("</PREAMBLE>") > 0)
                            taggedInput.append(line.substring(0, line.indexOf("</PREAMBLE>")) + "\n");

                    } else if (line.contains("</TEXT>")) {
                        includeLine = false;
                        if (line.indexOf("</TEXT>") > 0)
                            taggedInput.append(line.substring(0, line.indexOf("</TEXT>")) + "\n");

                    } else {
                        taggedInput.append(line + "\n");
                    }
                }
            }
            br.close();
        }
    }

    /**
     * Reads entities in taggedInput.
     * 
     * @return
     */
    protected String parse() {
        if (logger.isDebugEnabled())
            logger.debug("parse ...");

        input = taggedInput.toString().replaceAll("<p>|</p>", "");

        while (true) {

            int openTagStartIndex = input.indexOf("<ENAMEX");
            if (openTagStartIndex == -1)
                break;
            else {
                int openTagCloseIndex = input.indexOf(">", openTagStartIndex);
                int closeTagIndex = input.indexOf("</ENAMEX>");

                try {
                    String taggedWords = input.substring(openTagCloseIndex + 1, closeTagIndex);
                    String categoriesString = input.substring(openTagStartIndex + "<ENAMEX TYPE=\"".length(), openTagCloseIndex - "\"".length());

                    String[] categories = categoriesString.split("\\|");
                    for (String cat : categories) {
                        if (EntityClassMap.oracel(cat) != EntityClassMap.getNullCategory()) {

                            String[] token = FoxTextUtil.getSentenceToken(taggedWords + ".");
                            String word = "";
                            for (String t : token) {

                                if (!word.isEmpty() && t.isEmpty()) {
                                    put(word, cat);
                                    word = "";
                                } else
                                    word += t + " ";
                            }
                            if (!word.isEmpty())
                                put(word, cat);
                        }
                    }

                    String escapedCategoriesString = "";
                    for (String cat : categories)
                        escapedCategoriesString += cat + "\\|";

                    escapedCategoriesString = escapedCategoriesString.substring(0, escapedCategoriesString.length() - 1);

                    input = input.replaceFirst("<ENAMEX TYPE=\"" + escapedCategoriesString + "\">", "");
                    input = input.replaceFirst("</ENAMEX>", "");

                } catch (Exception e) {
                    logger.error("\n", e);
                }
            }
        }

        while (true) {
            int openTagStartIndex = input.indexOf("<TIMEX");
            if (openTagStartIndex == -1) {
                break;
            } else {
                int openTagCloseIndex = input.indexOf(">", openTagStartIndex);
                String category = input.substring(openTagStartIndex + "<TIMEX TYPE=\"".length(), openTagCloseIndex - 1);
                input = input.replaceFirst("<TIMEX TYPE=\"" + category + "\">", "");
                input = input.replaceFirst("</TIMEX>", "");
            }
        }

        input = input.trim();
        // input = input.replaceAll("``|''", "");
        // input = input.replaceAll("\\p{Blank}+", " ");
        // input = input.replaceAll("\n ", "\n");
        // input = input.replaceAll("\n+", "\n");
        // input = input.replaceAll("[.]+", ".");
        return input;
    }

    protected void put(String word, String classs) {
        word = word.trim();
        if (!word.isEmpty()) {
            if (entities.get(word) != null) {
                if (!entities.get(word).equals(classs) && !entities.get(word).equals(EntityClassMap.getNullCategory())) {
                    logger.info("Oracle with a token with diff. annos. No disamb. for now. Ignore token.");
                    logger.info(word + " : " + classs + " | " + entities.get(word));
                    entities.put(word, EntityClassMap.getNullCategory());
                }
            } else
                entities.put(word, classs);
        }
    }
}
