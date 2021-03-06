package edu.lipreading;

import edu.lipreading.classification.Classifier;
import edu.lipreading.classification.MultiLayerPerceptronClassifier;
import edu.lipreading.classification.SVMClassifier;
import edu.lipreading.classification.WekaClassifier;
import edu.lipreading.normalization.*;
import edu.lipreading.vision.AbstractFeatureExtractor;
import edu.lipreading.vision.ColoredStickersFeatureExtractor;
import weka.core.xml.XStream;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;


public class LipReading {

    public static void main(String... args) throws Exception {
        List<String> argsAsList = Arrays.asList(args);
        if (args.length == 0 || argsAsList.contains("-help")) {
            System.out.println("usage:");
            System.out.println("-help : prints this message");
            System.out.println("-extract <video file url, name or 0 for webcam> : extracts Sample object from the file into an xml");
            System.out.println("-output : when used with -extract records an output video");
            System.out.println("-dataset <path of folder of video files> : go through all the video files and" +
                    "\n generate Sample xmls for each of them");
            System.out.println("-test <video file url, name, 0 for webcam or xml> : uses the input file as test against a default data set");
            System.out.println("-test <video file url, name, 0 for webcam or xml>  <zip file url> : uses the input file as test against " +
                    "\n the data set in the given zip file url");
            System.out.println("-csv <input zip file, output file> : converts zip data set into csv one");
            System.exit(0);
        }

        Normalizer cn = new CenterNormalizer();
        AbstractFeatureExtractor fe = new ColoredStickersFeatureExtractor();

        if (argsAsList.contains("-extract")) {
            String sampleName = args[argsAsList.lastIndexOf("-extract") + 1];
            fe.setOutput(argsAsList.contains("-output"));
            XStream.write(sampleName.split("\\.")[0] + ".xml", cn.normalize(fe.extract(sampleName)));
        } else if (argsAsList.contains("-train")) {
            List<Sample> trainingSet = Utils.getTrainingSetFromZip(args[argsAsList.lastIndexOf("-arff") + 1]);
            WekaClassifier svmc = new SVMClassifier();
            svmc.train(trainingSet);
            svmc.saveToFile(args[argsAsList.lastIndexOf("-arff") + 2]);
        } else if (argsAsList.contains("-dataset")) {
            dataset(fe, args[argsAsList.lastIndexOf("-dataset") + 1]);
        } else if (argsAsList.contains("-test") && argsAsList.size() > argsAsList.lastIndexOf("-test") + 2) {
            test(fe, args[argsAsList.lastIndexOf("-test") + 1], args[argsAsList.lastIndexOf("-test") + 2]);
        } else if (argsAsList.contains("-test")) {
            test(fe, args[argsAsList.lastIndexOf("-test") + 1], Constants.DEFAULT_TRAINING_SET_ZIP);
        } else if (argsAsList.contains("-csv")) {
            List<Sample> trainingSet = Utils.getTrainingSetFromZip(args[argsAsList.lastIndexOf("-csv") + 1]);
            Utils.dataSetToCSV(trainingSet, args[argsAsList.lastIndexOf("-csv") + 2]);
        } else if (argsAsList.contains("-arffs")) {
            List<Sample> bigDataSet = Utils.getTrainingSetFromZip(args[argsAsList.lastIndexOf("-arffs") + 1]);
            String arffFilePath = args[argsAsList.lastIndexOf("-arffs") + 2];
            new File(arffFilePath).mkdirs();
            String arffFile = args[argsAsList.lastIndexOf("-arffs") + 3];

            createIncrementalArffs(bigDataSet, arffFilePath, arffFile);
        } else if (argsAsList.contains("-arff")) {
            List<Sample> trainingSet = Utils.getTrainingSetFromZip(args[argsAsList.lastIndexOf("-arff") + 1]);
            Utils.dataSetToARFF(trainingSet, args[argsAsList.lastIndexOf("-arff") + 2]);
        } else if (argsAsList.contains("-verifyModel")) {
            List<Sample> trainingSet = Utils.getTrainingSetFromZip(args[argsAsList.lastIndexOf("-verifyModel") + 1]);
            String model = args[argsAsList.lastIndexOf("-verifyModel") + 2];
            Classifier classifier = new SVMClassifier(model);
            testModel(trainingSet, classifier);
        } else {
            System.out.println("Unknown argument");
        }
        System.exit(0);
    }

    private static void createIncrementalArffs(List<Sample> bigDataSet, String arffFilePath, String arffFile) throws Exception {
        Map<String, List<Sample>> dataSetMap = new HashMap<String, List<Sample>>();
        for (Sample sample : bigDataSet) {
            if (!dataSetMap.containsKey(sample.getLabel())) {
                dataSetMap.put(sample.getLabel(), new Vector<Sample>());
            }
            dataSetMap.get(sample.getLabel()).add(sample);
        }
        int maxSize = Integer.MIN_VALUE;
        for (List<Sample> samples : dataSetMap.values()) {
            if (samples.size() > maxSize) {
                maxSize = samples.size();

            }
        }
        for (int i = 5; i <= maxSize; i += 5) { //step up starting from 5
            List<Sample> smallDataSet = createRandomDataSet(dataSetMap, i);
            String arffActualFile = arffFilePath + "\\" + arffFile + i + ".arff";
            System.out.println(arffActualFile);
            Utils.dataSetToARFF(smallDataSet, arffActualFile);
        }
    }

    private static void testModel(List<Sample> trainingSet, Classifier classifier) {
        int correct = 0;
        for (Sample sample : trainingSet) {
            try {
                String output = classifier.test(normalize(sample));
                if (sample.getLabel().equals(output)) {
                    correct++;
                } else {
                    System.out.println("Sample: " + sample.getLabel() + " | Classified as: " + output);
                }
            } catch (Exception e) {
            }
        }
        System.out.println("\nModel Accuracy Rate: " + (correct * 100.0) / trainingSet.size() + "%");
    }


    private static List<Sample> createRandomDataSet(Map<String, List<Sample>> dataSetMap, int instancesPerWord) {
        List<Sample> smallDataSet = new Vector<Sample>();
        for (List<Sample> samples : dataSetMap.values()) {
            List<Sample> toAdd = new Vector<Sample>();
            if (samples.size() < instancesPerWord) {
                toAdd.addAll(samples);
            } else {
                while (toAdd.size() < instancesPerWord) {
                    int index = (int) (Math.random() * samples.size());
                    if (!toAdd.contains(samples.get(index))) {
                        toAdd.add(samples.get(index));
                    }
                }
            }
            smallDataSet.addAll(toAdd);
        }
        return smallDataSet;
    }

    private static void test(AbstractFeatureExtractor fe, String testFile, String trainigSetZipFile) throws Exception {
        File modelFile = new File(Constants.MPC_MODEL_URL);
        if (Utils.isSourceUrl(Constants.MPC_MODEL_URL)) {
            modelFile = new File(Utils.getFileNameFromUrl(trainigSetZipFile));
            if (!modelFile.exists()) {
                Utils.get(Constants.MPC_MODEL_URL);
            }
        }
        Classifier classifier = new MultiLayerPerceptronClassifier(new FileInputStream(modelFile));
        Sample sample = fe.extract(testFile);
        sample = normalize(sample);
        System.out.println("got the word: " +
                classifier.test(sample));
    }

    private static void dataset(AbstractFeatureExtractor fe, String folderPath) throws Exception {
        File samplesDir = new File(folderPath);
        for (String sampleName : samplesDir.list()) {
            File sampleFile = new File(samplesDir.getAbsolutePath() + "/" + sampleName);
            if (sampleFile.isFile() && sampleFile.getName().contains("MOV")) {
                Sample sample = fe.extract(sampleFile.getAbsolutePath());
                sample = normalize(sample);
                XStream.write(sampleName.split("\\.")[0] + ".xml", sample);
            }
        }
    }

    private static Sample normalize(Sample sample, Normalizer... normalizers) {
        if (sample.getOriginalMatrixSize() == 0) {
            sample.setOriginalMatrixSize(sample.getMatrix().size());
        }
        for (Normalizer normalizer : normalizers) {
            sample = normalizer.normalize(sample);
        }
        return sample;
    }

    /**
     * Normalize sample using predefined normalizers
     *
     * @param sample
     * @return
     */
    public static Sample normalize(Sample sample) {
        Normalizer sfn = new SkippedFramesNormalizer(),
                cn = new CenterNormalizer(),
                /* rotation and resolution cause some trouble right now on models not trained with them.*/
                rotn = new RotationNormalizer(),
                resn = new ResolutionNormalizer(),
                tn = new LinearStretchTimeNormalizer();
        return normalize(sample, sfn, cn, /*rotn, resn,*/ tn);
    }

}
