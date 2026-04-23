package org.retrosynthesis.chem;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IReaction;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmiFlavor;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.openscience.cdk.smiles.SmilesParser;

public class SmilesXMol {


    public static IAtomContainer mol(String smiles) throws InvalidSmilesException {

        SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());

        return parser.parseSmiles(smiles);

    }

    public static IReaction reaction(String rxnsmiles) throws InvalidSmilesException {

        SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());

        return parser.parseReactionSmiles(rxnsmiles);

    }

    /**
     * Generates an absolute SMILES
     *
     * @param mol
     * @return
     * @throws CDKException
     */
    public static String smiles(IAtomContainer mol) throws CDKException {
        //SmilesGenerator.absolute()
        return SmilesGenerator.absolute().create(mol);
    }

    public static String smiles(IAtomContainer mol, boolean isomeric) throws CDKException {

        if (isomeric) {
            return SmilesGenerator.absolute().create(mol);
        } else {
            return SmilesGenerator.unique().create(mol);
        }
    }

    /**
     * Generates an isomeric, non-canonical, mapped reaction SMILES
     *
     * @param rxn
     * @return Reaction SMILES string
     * @throws CDKException
     */
    public static String smiles(IReaction rxn) throws CDKException {

        SmilesGenerator gen = new SmilesGenerator(
                SmiFlavor.Isomeric
                        | SmiFlavor.AtomAtomMap
                //        | SmiFlavor.AtomAtomMapRenumber
        );

        // return SmilesGenerator.absolute().create(rxn);
        return gen.create(rxn);
    }

    public static String unmapped(IReaction rxn) throws CDKException {
//        SmilesGenerator gen = new SmilesGenerator(SmiFlavor.Isomeric | SmiFlavor.AtomAtomMap);
        // return SmilesGenerator.absolute().create(rxn);
        return SmilesGenerator.unique().create(rxn);
    }


}
