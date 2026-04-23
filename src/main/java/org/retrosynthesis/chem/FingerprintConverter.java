package org.retrosynthesis.chem;

import org.openscience.cdk.fingerprint.ICountFingerprint;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class FingerprintConverter {

    /** Modulus to use for the hash function */
    public FingerprintConverter(int fold) {
        this.fold = fold;
    }

    /** Modulus to use for the hash function */
    public final int fold;


    public static String compressLong(long value) {
        return BigInteger.valueOf(value).toString(36);
    }

    public static long decompressString(String compressed) {
        return new BigInteger(compressed, 36).longValue();
    }

    public static Map<Integer, Integer> convert(ICountFingerprint fingerprint) {

		final TreeMap<Integer, Integer> map = new TreeMap<Integer, Integer>();
	
		for (int i = 0; i < fingerprint.numOfPopulatedbins(); i++) {
			int hash = fingerprint.getHash(i);
			if (map.containsKey(hash)) {
				map.put(hash, map.get(hash) + fingerprint.getCount(i));
			} else {
				map.put(hash, fingerprint.getCount(i));
			}
		}
		return map;
	}

    public List<String> processCountFingerprint(ICountFingerprint fingerprint) {

        List<String> fields = new ArrayList<>();

        int bins = fingerprint.numOfPopulatedbins();

        for (int i = 0; i < bins; i++) {

            int hash = Math.abs(fingerprint.getHash(i)) % (fold);
            int count = fingerprint.getCount(i);
            int sign = count >= 0 ? 1 : -1;

            int abscount = Math.abs(count);

            String hashString = Integer.toString(hash * sign);

            for (int j = 0; j < abscount; j++) {

                fields.add(hashString);

            }
        }
        return fields;
    }
}
