package org.retrosynthesis.chem;

import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IReaction;
import org.openscience.cdk.isomorphism.Pattern;
import org.openscience.cdk.smarts.SmartsPattern;


public class SmartsFilter {

    
    private final Pattern pattern;
    private final boolean isReaction;

    public SmartsFilter(String smarts) throws IllegalArgumentException{

        if (smarts == null || smarts.isBlank()) {
            this.pattern = null;
            this.isReaction = false;
        } else {
            this.pattern = SmartsPattern.create(smarts, null);
            this.isReaction = smarts.contains(">");
        }

    }

    public boolean filter(IReaction rxn) {
        return pattern == null || pattern.matches(rxn);
    }

    public boolean filter(IAtomContainer mol) {
        return pattern == null || pattern.matches(mol);
    }
}