package org.retrosynthesis.chem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.fingerprint.CircularFingerprinter;
import org.openscience.cdk.fingerprint.ICountFingerprint;
import org.openscience.cdk.fingerprint.IFingerprinter;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IReaction;


public class ReactionFingerprinter<T> implements IReactionFingerprinter<Integer> {

	private IFingerprinter fingerprinter;

	/**
	 * 
	 * Constructs a ReactionFingerprinter based on ECFP4
	 * 
	 */
	public ReactionFingerprinter() {
		this.fingerprinter = new CircularFingerprinter(CircularFingerprinter.CLASS_ECFP4);
	}

	/**
	 * 
	 * The Fingerprinter that is injected must implement the getCountFingerprint(...) method. 
	 * 
	 */
	public ReactionFingerprinter(IFingerprinter fingerprinter) {

		this.fingerprinter = fingerprinter;
	}

	/**
	 * 
	 * Add collection of raw fingerprints
	 * 
	 * @param fingerprints
	 * @return
	 */

	private Map<Integer, Integer> sum(List<ICountFingerprint> fingerprints) {

		final TreeMap<Integer, Integer> map = new TreeMap<Integer, Integer>();
		for (ICountFingerprint fingerprint : fingerprints) {
			for (int i = 0; i < fingerprint.numOfPopulatedbins(); i++) {
				int hash = fingerprint.getHash(i);
				if (map.containsKey(hash)) {
					map.put(hash, map.get(hash) + fingerprint.getCount(i));
				} else {
					map.put(hash, fingerprint.getCount(i));
				}
			}
		}
		return map;
	}

	private Map<Integer, Integer> subtract(Map<Integer, Integer> products, Map<Integer, Integer> reactants) {

		Map<Integer, Integer> map = new HashMap<>();
		map.putAll(products);

		for (Integer hash : reactants.keySet()) {

			if (map.containsKey(hash)) {
				
				int count = map.get(hash) - reactants.get(hash);
				
				if (count == 0) {
					map.remove(hash);
				} else {
					map.put(hash, count);
				}
				//map.put(hash, count);
			} else {
				int count = -reactants.get(hash);
				map.put(hash, count);
			}
		}
		return map;

	}

	public ICountFingerprint transform(Map<Integer, Integer> rawFingerprint) {

		// extract a convenient {hash:count} datastructure

		final Map<Integer, Integer> map = new TreeMap<Integer, Integer>(rawFingerprint);
		// map.putAll(rawFingerprint);

		final int sz = map.size();
		final int[] hash = new int[sz], count = new int[sz];
		int n = 0;
		for (int h : map.keySet()) {
			hash[n] = h;
			count[n++] = map.get(h);
		}

		// implement a custom instance that provides a window directly into the
		// summary content
		return new ICountFingerprint() {
			private static final long serialVersionUID = 8656072054328822795L;

			public long size() {
				return 4294967296l;
			}

			public int numOfPopulatedbins() {
				return sz;
			}

			public int getCount(int index) {
				return count[index];
			}

			public int getHash(int index) {
				return hash[index];
			}

			public void merge(ICountFingerprint fp) {
			}

			public void setBehaveAsBitFingerprint(boolean behaveAsBitFingerprint) {
			}

			public boolean hasHash(int hash) {
				return map.containsKey(hash);
			}

			public int getCountForHash(int hash) {
				return map.containsKey(hash) ? map.get(hash) : 0;
			}
		};
	}

	public ICountFingerprint getCountFingerprint(IReaction reaction) {

		return transform(getRawFingerprint(reaction));
	}

	@Override
	public Map<Integer, Integer> getRawFingerprint(IReaction reaction) {

		List<ICountFingerprint> reactantFingerprints = new ArrayList<>();
		List<ICountFingerprint> productFingerprints = new ArrayList<>();
		try {
			for (IAtomContainer reactant : reaction.getReactants().atomContainers()) {
				reactantFingerprints.add(fingerprinter.getCountFingerprint(reactant));
			}

			for (IAtomContainer product : reaction.getProducts().atomContainers()) {
				productFingerprints.add(fingerprinter.getCountFingerprint(product));
			}
		} catch (CDKException e) {
			System.out.println("Error calculating Reaction Fingerprint");
		}

		Map<Integer, Integer> reactantSum = sum(reactantFingerprints);
		Map<Integer, Integer> productSum = sum(productFingerprints);
		
		return subtract(productSum, reactantSum);
	}

}
