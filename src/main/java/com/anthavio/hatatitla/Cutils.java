package com.anthavio.hatatitla;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Digest from various apache commons libraries
 * 
 * @author martin.vanek
 *
 */
public class Cutils {

	private Cutils() {
		//prevent new instance
	}

	public static boolean isEmpty(String s) {
		return s == null || s.length() == 0;
	}

	public static boolean isBlank(String s) {
		int strLen;
		if (s == null || (strLen = s.length()) == 0) {
			return true;
		}
		for (int i = 0; i < strLen; i++) {
			if (Character.isWhitespace(s.charAt(i)) == false) {
				return false;
			}
		}
		return true;
	}

	public static boolean isNotBlank(String str) {
		return !isBlank(str);
	}

	public static void close(Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (Exception x) {
				//ignore quietly
			}
		}
	}

	public static void close(InputStream i) {
		if (i != null) {
			try {
				i.close();
			} catch (Exception x) {
				//ignore quietly
			}
		}
	}

	public static void close(OutputStream o) {
		if (o != null) {
			try {
				o.close();
			} catch (Exception x) {
				//ignore quietly
			}
		}
	}

}
