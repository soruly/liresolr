package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.imageanalysis.features.GlobalFeature;
import net.semanticmetadata.lire.imageanalysis.features.global.ACCID;
import net.semanticmetadata.lire.imageanalysis.features.global.AutoColorCorrelogram;
import net.semanticmetadata.lire.imageanalysis.features.global.CEDD;
import net.semanticmetadata.lire.imageanalysis.features.global.ColorLayout;
import net.semanticmetadata.lire.imageanalysis.features.global.EdgeHistogram;
import net.semanticmetadata.lire.imageanalysis.features.global.FCTH;
import net.semanticmetadata.lire.imageanalysis.features.global.FuzzyOpponentHistogram;
import net.semanticmetadata.lire.imageanalysis.features.global.GenericGlobalIntFeature;
import net.semanticmetadata.lire.imageanalysis.features.global.JCD;
import net.semanticmetadata.lire.imageanalysis.features.global.OpponentHistogram;
import net.semanticmetadata.lire.imageanalysis.features.global.PHOG;
import net.semanticmetadata.lire.imageanalysis.features.global.ScalableColor;
import net.semanticmetadata.lire.imageanalysis.features.global.joint.JointHistogram;
import net.semanticmetadata.lire.imageanalysis.features.global.spatialpyramid.SPCEDD;
import net.semanticmetadata.lire.solr.features.DoubleFeatureCosineDistance;
import net.semanticmetadata.lire.solr.features.ShortFeatureCosineDistance;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * This file is part of LIRE Solr, a Java library for content based image retrieval.
 *
 * @author Mathias Lux, mathias@juggle.at, 28.11.2014
 */
public final class FeatureRegistry {

    public static final String featureFieldPostfix = "_hi";   // contains the histogram
    public static final String hashFieldPostfix = "_ha";      // contains the hash
    public static final String metricSpacesFieldPostfix = "_ms";      // contains the hash

    /**
     * Naming conventions for code: 2 letters for global features. More for local ones.
     */
    private static final HashMap<String, Class<? extends GlobalFeature>> codeToClass = new HashMap<>();
    /**
     * Caching the entries for fast retrieval or Strings without generating new objects.
     */
    private static final HashMap<String, Class<? extends GlobalFeature>> hashFieldToClass = new HashMap<>();
    private static final HashMap<String, Class<? extends GlobalFeature>> featureFieldToClass = new HashMap<>();
    private static final HashMap<String, String> hashFieldToFeatureField = new HashMap<>();
    private static final HashMap<Class<? extends GlobalFeature>, String> classToCode = new HashMap<>();
    private static final Set<String> SUPPORTED_CODES;

    static {
        codeToClass.put("cl", ColorLayout.class);
        codeToClass.put("eh", EdgeHistogram.class);
        codeToClass.put("jc", JCD.class);
        codeToClass.put("oh", OpponentHistogram.class);
        codeToClass.put("ph", PHOG.class);
        codeToClass.put("ac", AutoColorCorrelogram.class);
        codeToClass.put("ad", ACCID.class);
        codeToClass.put("ce", CEDD.class);
        codeToClass.put("fc", FCTH.class);
        codeToClass.put("fo", FuzzyOpponentHistogram.class);
        codeToClass.put("jh", JointHistogram.class);
        codeToClass.put("sc", ScalableColor.class);
        codeToClass.put("pc", SPCEDD.class);
        codeToClass.put("df", DoubleFeatureCosineDistance.class);
        codeToClass.put("if", GenericGlobalIntFeature.class);
        codeToClass.put("sf", ShortFeatureCosineDistance.class);

        SUPPORTED_CODES = Set.copyOf(codeToClass.keySet());

        for (String code : codeToClass.keySet()) {
            hashFieldToClass.put(code + hashFieldPostfix, codeToClass.get(code));
            featureFieldToClass.put(code + featureFieldPostfix, codeToClass.get(code));
            hashFieldToFeatureField.put(code + hashFieldPostfix, code + featureFieldPostfix);
            classToCode.put(codeToClass.get(code), code);
        }
    }

    /**
     * Used to retrieve a registered class for a given hash field name.
     *
     * @param hashFieldName the name of the hash field
     * @return the class for the given field or null if not registered.
     */
    public static Class<? extends GlobalFeature> getClassForHashField(String hashFieldName) {
        return hashFieldToClass.get(hashFieldName);
    }


    /**
     * Used to retrieve a registered class for a given field name in SOLR for the feature.
     *
     * @param featureFieldName the name of the field containing the histogram
     * @return the class for the given field or null if not registered.
     */
    public static Class<? extends GlobalFeature> getClassForFeatureField(String featureFieldName) {
        return featureFieldToClass.get(featureFieldName);
    }

    /**
     * Returns the feature's histogram field for a given hash field.
     *
     * @param hashFieldName the name of the hash field
     * @return the name or null if the feature is not registered.
     */
    public static String getFeatureFieldName(String hashFieldName) {
        return hashFieldToFeatureField.get(hashFieldName);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Registered features:\n");
        sb.append("code\thash field\tfeature field\tclass\n");

        for (String code : codeToClass.keySet()) {
            sb.append(code);
            sb.append('\t');
            sb.append(code).append(hashFieldPostfix);
            sb.append('\t');
            sb.append(code).append(featureFieldPostfix);
            sb.append('\t');
            sb.append(codeToClass.get(code).getName());
            sb.append('\n');
        }

        return sb.toString();
    }

    public static String getCodeForClass(Class<? extends GlobalFeature> featureClass) {
        return classToCode.get(featureClass);
    }

    public static Class<? extends GlobalFeature> getClassForCode(String code) {
        return codeToClass.get(code);
    }

    public static String codeToHashField(String code) {
        return code + hashFieldPostfix;
    }

    public static String codeToMetricSpacesField(String code) {
        return code + metricSpacesFieldPostfix;
    }

    public static String codeToFeatureField(String code) {
        return code + featureFieldPostfix;
    }

    public static Collection<String> getSupportedCodes() {
        return SUPPORTED_CODES;
    }
}
