package com.bigmarker.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class LibraryExtractor {

	/**
	 * Detects the current operating system and cpu architecture. After
	 * detection, the correct libraries are extracted for use by the native
	 * methods.
	 */
	public final static void detectAndExtract() {
		// determine os
		String os = "win";
		String libFileName = null;
		String osName = System.getProperty("os.name", "win").toLowerCase();
		if ((osName.indexOf("mac") >= 0) || (osName.indexOf("darwin") >= 0)) {
			os = "osx";
			libFileName = "libxuggle.dylib";
		} else if (osName.indexOf("win") >= 0) {
			os = "win";
			libFileName = "xuggle.dll";
		} else if (osName.indexOf("nux") >= 0) {
			os = "linux";
			libFileName = "libxuggle.so";
		}
		System.out.println("OS: " + os);
		// determine 64 or 32 bit
		// we first check the sun value for the current vm we're running within
		String cpuArch = System.getProperty("sun.arch.data.model");
		if ("unknown".equals(cpuArch)) {
			// we got unknown so check additional properties
			if ((System.getProperty("os.arch").indexOf("64") != -1) || (System.getenv("PROCESSOR_IDENTIFIER").indexOf("64") != -1)
					|| (System.getenv("JAVA_ARCH").indexOf("64") != -1)) {
				cpuArch = "64";
			}
		}
		System.out.println("JVM cpu arch: " + cpuArch);
		// we should be running from inside the xuggler native jar
		String path = LibraryExtractor.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		System.out.println("Current runtime path: " + path);
		String destPath = System.getProperty("java.io.tmpdir");
		System.out.println("Working directory: " + System.getProperty("user.dir") + " destination: " + destPath);
		// get resource from inside current jar
		InputStream is = LibraryExtractor.class.getClassLoader().getResourceAsStream(String.format("%s/%s", cpuArch, libFileName));
		if (is != null) {
			LibraryExtractor.extractLibrary(is, destPath, libFileName);
		}
		// get via path
		//LibraryExtractor.extractLibrary(path, destPath, cpuArch);
		if (new File(destPath, libFileName).exists()) {
			System.out.println("Library found");
			// add library to native path
			LibraryExtractor.addToLibraryPath(destPath);
		} else {
			System.out.println("Library lookup failed");
		}
	}

	public final static void extractLibrary(InputStream in, String destPath, String libName) {
		try {
			FileOutputStream out = new FileOutputStream(new File(destPath, libName));
			byte[] buffer = new byte[1024];
			int len;
			while ((len = in.read(buffer)) != -1) {
				out.write(buffer, 0, len);
			}
			out.flush();
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public final static void extractLibrary(String srcPath, String destPath, String cpuArch) {
		JarFile jf = null;
		try {
			jf = new JarFile(srcPath);
			Enumeration<JarEntry> entries = jf.entries();
			while (entries.hasMoreElements()) {
				JarEntry je = entries.nextElement();
				String name = je.getName();
				System.out.println("Jar entry: " + name);
				if (name.startsWith(cpuArch) && name.indexOf("xuggle") != -1) {
					InputStream in = jf.getInputStream(je);
					FileOutputStream out = new FileOutputStream(new File(destPath, name.substring(name.indexOf('/'), name.length())));
					byte[] buffer = new byte[1024];
					int len;
					while ((len = in.read(buffer)) != -1) {
						out.write(buffer, 0, len);
					}
					out.flush();
					out.close();
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (jf != null) {
				try {
					jf.close();
				} catch (IOException e) {
				}
				jf = null;
			}
		}
	}

	public final static void addToLibraryPath(String path) {
		try {
			// This enables the java.library.path to be modified at runtime
			// From a Sun engineer at http://forums.sun.com/thread.jspa?threadID=707176
			Field field = ClassLoader.class.getDeclaredField("usr_paths");
			field.setAccessible(true);
			String[] paths = (String[]) field.get(null);
			for (int i = 0; i < paths.length; i++) {
				if (path.equals(paths[i])) {
					return;
				}
			}
			String[] tmp = new String[paths.length + 1];
			System.arraycopy(paths, 0, tmp, 0, paths.length);
			tmp[paths.length] = path;
			field.set(null, tmp);
			System.setProperty("java.library.path", System.getProperty("java.library.path") + File.pathSeparator + path);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
