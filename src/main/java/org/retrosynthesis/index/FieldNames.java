package org.retrosynthesis.index;

public class FieldNames {

    public static final String FIELD_RXN_ID = "xid";
    public static final String FIELD_RXN_SMILES = "rxn_smiles";
    public static final String FIELD_RXNFP_ECFP = "rxn_ecfp"; // indexed with DOCS_AND_FREQS
    public static final String FIELD_RXNFP_PATH = "rxn_pathfp"; // indexed with DOCS_AND_FREQS
    public static final String FIELD_PRODFPT_PATH = "prod_pathfp"; // indexed with DOCS_AND_FREQS
    public static final String FIELD_PRODFPT_ECFP = "prod_ecfp"; // indexed with DOCS_AND_FREQS
    public static final String FIELD_FP_COUNTS = "fp_counts"; // stored CSV
    public static final String FIELD_FP_L1 = "fp_l1"; // NumericDocValues for sum(counts)

}
