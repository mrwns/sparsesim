package org.retrosynthesis.chem;

import java.util.Map;

import org.openscience.cdk.fingerprint.ICountFingerprint;
import org.openscience.cdk.interfaces.IReaction;

public interface IReactionFingerprinter<T> {
	
	public ICountFingerprint getCountFingerprint(IReaction reaction);
	
	public Map<T,Integer> getRawFingerprint(IReaction reaction);

}
