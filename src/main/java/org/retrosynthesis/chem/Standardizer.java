package org.retrosynthesis.chem;

import org.openscience.cdk.aromaticity.Aromaticity;
import org.openscience.cdk.aromaticity.ElectronDonation;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.graph.Cycles;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IReaction;

public class Standardizer {

    private Aromaticity arom;

    public Standardizer() {
        this.arom = new Aromaticity(ElectronDonation.daylight(), Cycles.or(Cycles.all(), Cycles.relevant()));

    }

    /**
     * Assign Daylight aromaticity.
     *
     * @param rxn
     * @throws CDKException
     */
    public void standardize(IReaction rxn) throws CDKException {

        for (IAtomContainer mol : rxn.getReactants().atomContainers()) {
            arom.apply(mol);
        }

        for (IAtomContainer mol : rxn.getProducts().atomContainers()) {
            arom.apply(mol);
        }

    }


    public void standardize(IAtomContainer mol) throws CDKException {

        arom.apply(mol);

    }


}
