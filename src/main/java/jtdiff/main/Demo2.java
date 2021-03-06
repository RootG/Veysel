package jtdiff.main;

import jtdiff.antlr.ParseTreeCache;
import jtdiff.antlr.ParseTreeWrapper;
import jtdiff.pattern.DataExtractionContext;
import jtdiff.pattern.Pattern;
import jtdiff.pattern.PatternRegistry;
import jtdiff.util.DiffToDot;
import jtdiff.util.EditOperation;
import jtdiff.util.ImageFromDot;
import jtdiff.util.MappingUtil;
import jtdiff.util.Tree;
import jtdiff.util.YAMLToTree;
import jtdiff.util.YamlToBracketConverter;

import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogManager;

/**
 * Takes two serialized trees in yaml format and produce a png diff image.
 *
 * Instructions:
 *
 * - Run this by itself (under src/main/java:
 *    javac -cp .:/usr/local/lib/yamlbeans-1.09.jar:/usr/local/lib/antlr-4.6-complete.jar jtdiff/main/Demo2.java; \
 *    java -Djava.util.logging.config.file=jtdiff/main/demo.logging.properties -cp .:/usr/local/lib/yamlbeans-1.09.jar:/usr/local/lib/antlr-4.6-complete.jar jtdiff.main.Demo2 jtdiff/main/demoSourceCodeOriginal jtdiff/main/demoSourceCodeUpdated
 *
 * - Run this by maven (under the root folder):
 *    mvn compile; \
 *    mvn exec:java -Dexec.mainClass="jtdiff.main.Demo2"
 *
 * - Log file is available at (As defined in src/main/java/jtdiff/main/demo.logging.properties):
 *    /tmp/veysel_demo.log
 */
public class Demo2 {
  private final static Logger LOGGER = Logger.getLogger(Demo.class.getName());
  private final static String LOCATION_FOR_DEMO_SOURCE_CODE_ORIGINAL =
      "src/main/java/jtdiff/main/demoSourceCodeOriginal";
  private final static String LOCATION_FOR_DEMO_SOURCE_CODE_UPDATED =
      "src/main/java/jtdiff/main/demoSourceCodeUpdated";
  private final static String LOCATION_FOR_DEMO_TREES_IN_YAML =
      "/tmp/trees.yaml";
  private final static String LOCATION_FOR_PNG_IMAGE = "/tmp/demoDiffImage.png";
  private final static String LOCATION_FOR_LOG_PROPERTIES_FILE_FOR_MAVEN =
      "src/main/java/jtdiff/main/demo.logging.properties";

  public static void main(String[] args) throws Exception {
    LOGGER.setLevel(Level.INFO);
    LOGGER.info("Info Log");

    if (args.length == 0) { // Started by maven
        // It is tricky to configure the logging.properties file location
        // for maven. Therefore we do it dynamically.
        Properties preferences = new Properties();
        try {
            FileInputStream configFile =
                new FileInputStream(
                    LOCATION_FOR_LOG_PROPERTIES_FILE_FOR_MAVEN);
            LogManager.getLogManager().readConfiguration(configFile);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println(
                "WARNING: Could not open configuration file");
            System.out.println(
                "WARNING: Logging not configured (console output only)");
            return;
        }
    }

    // File locations are different when called from maven vs. java. So we
    // pass an explicit argument for that when executed from java.
    String originalCodeLocation = LOCATION_FOR_DEMO_SOURCE_CODE_ORIGINAL;
    String updatedCodeLocation = LOCATION_FOR_DEMO_SOURCE_CODE_UPDATED;
    if (args.length > 0) {  // Started by java (not maven)
        originalCodeLocation = args[0];
        updatedCodeLocation = args[1];
    }

    ParseTreeCache sourceParseTree = new ParseTreeCache();
    sourceParseTree.build(
        new FileInputStream(new File(originalCodeLocation)));
    String originalYaml = sourceParseTree.yamlSerialization();


    ParseTreeCache targetParseTree = new ParseTreeCache();
    targetParseTree.build(
        new FileInputStream(new File(updatedCodeLocation)));
    String updatedYaml = targetParseTree.yamlSerialization();

    String combinedYaml = originalYaml + "\n---\n" + updatedYaml;

    LOGGER.info("combined yaml:\n" + combinedYaml);
    LOGGER.info("combined yaml done");
    PrintStream out = new PrintStream(
        new FileOutputStream(LOCATION_FOR_DEMO_TREES_IN_YAML));
    out.print(combinedYaml);
    out.close();


    LOGGER.info("Reading the yaml file for deserialization: " +
                LOCATION_FOR_DEMO_TREES_IN_YAML);
    InputStreamReader reader = new FileReader(LOCATION_FOR_DEMO_TREES_IN_YAML);
    List<Tree> trees = YAMLToTree.buildTreesFromYamlInput(reader);
    Iterator<Tree> iter = trees.iterator();
    Tree sourceTree = iter.next();
    Tree targetTree = iter.next();
    LOGGER.info("Trees are deserialized.");

    LOGGER.info("Difference is being computed...");
    boolean isPreorder;
    int emptyLabelValue;

    /** When using Custom TDiff Implementation **/
    //isPreorder = true;
    //emptyLabelValue = Constants.ALPHA_INT;
    //Result result = TreeDiff.computeDiff(sourceTree, targetTree);

    /** When using APTED implementatin **/
    String originalInBracket = YamlToBracketConverter.convert(originalYaml);
    String updatedInBracket = YamlToBracketConverter.convert(updatedYaml);
    Result result = APTEDWrapper.diff(originalInBracket, updatedInBracket);
    isPreorder = false;
    emptyLabelValue = 0;

    LOGGER.info("Cost: " + result.cost);
    LOGGER.info("Difference: " + MappingUtil.produceHumanFriendlyMapping(
        result.mapping, sourceTree, targetTree,
        isPreorder, emptyLabelValue));

    LOGGER.info("Dot is being generated...");
    String dot = DiffToDot.generateDotFromDiff(
        sourceTree, targetTree, result.mapping, isPreorder, emptyLabelValue);
    LOGGER.info("Dot representation:\n" + dot);

    String pngLocation = LOCATION_FOR_PNG_IMAGE;
    LOGGER.info(String.format(
        "Creating a png image at \"%s\" from the dot...", pngLocation));
    ImageFromDot.generatePngFromDot(dot, pngLocation);
    LOGGER.info("Starting the image viewer...");
    new ProcessBuilder("eog", pngLocation).start();

    List<EditOperation> editList = MappingUtil.produceEditOperationList(
        result.mapping, sourceTree, targetTree,
        isPreorder, emptyLabelValue);
    generateViews(editList, sourceParseTree, targetParseTree, isPreorder);
  }

  public static void generateViews(List<EditOperation> editList,
                                   ParseTreeCache sourceParseTree,
                                   ParseTreeCache targetParseTree,
                                   boolean isPreorder) {
    for (EditOperation editOperation : editList) {
      if (editOperation.type() == EditOperation.Type.NO_CHANGE) {
        continue;
      }

      ParseTreeWrapper nodeInSource = null;
      ParseTreeWrapper nodeInTarget = null;
      DataExtractionContext extractionCtx = new DataExtractionContext()
          .sourceParser(sourceParseTree.parser())
          .targetParser(targetParseTree.parser());

      switch (editOperation.type()) {
        case CHANGE:
          {
            int sourcePosition = editOperation.sourceNodePosition();
            int targetPosition = editOperation.targetNodePosition();
            if (isPreorder) {
              nodeInSource = sourceParseTree.getParseTreeAtPreorderPosition(
                  sourcePosition);
              nodeInTarget = targetParseTree.getParseTreeAtPreorderPosition(
                  targetPosition);
            } else {
              nodeInSource = sourceParseTree.getParseTreeAtPostorderPosition(
                  sourcePosition);
              nodeInTarget = targetParseTree.getParseTreeAtPostorderPosition(
                  targetPosition);
            }
          }
        break;

        case INSERT:
          {
            int position = editOperation.targetNodePosition();
            if (isPreorder) {
              nodeInTarget = targetParseTree.getParseTreeAtPreorderPosition(
                  position);
            } else {
              nodeInTarget = targetParseTree.getParseTreeAtPostorderPosition(
                  position);
            }
          }
        break;
        
        case DELETE:
          {
            int position = editOperation.sourceNodePosition();
            if (isPreorder) {
              nodeInSource = sourceParseTree.getParseTreeAtPreorderPosition(
                  position);
            } else {
              nodeInSource = sourceParseTree.getParseTreeAtPostorderPosition(
                  position);
            }
          }
        break;
      }  // switch

      Pattern pattern = PatternRegistry.match(
          extractionCtx, nodeInSource, nodeInTarget);
      if (pattern == null) {
        continue;
      }
      String view = pattern.viewTemplate().generateView(
          extractionCtx, nodeInSource, nodeInTarget);
      System.out.println(view);
    }
  }
}