package org.example.bundle;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.stream.Stream;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

public class NativesUtils {
	
	public static void init(BundleContext context) {		
		// Need to make sure native libraries are loaded from correct path and/or
		// are available in correct form. When sources are being executed from 
		// eclipse project, all native libraries are copied to target/classes folder
		// and are accessible from there.
		// In case project is being built into a jar, we can no longer load native
		// libraries directly, as they are in zip format within resulting jar, and
		// system does not work with resource stream (as it probably should). This
		// means we have to copy native libraries outside of our jar and point 
		// the library path to their new location.
		try {
			String libPath = "/natives/"+getSystemDescriptor();
			
			if (isJar(NativesUtils.class)) {
				// Set library path to new temporary location.
				String jarPath = new File(NativesUtils.class.getProtectionDomain()
						.getCodeSource().getLocation().toURI()).getParent();
				System.setProperty("java.library.path", 
						System.getProperty("java.library.path") 
						+ File.pathSeparator + jarPath + "/" + libPath);

				String dst = new File(getRootPath(NativesUtils.class)).getParent();
				Path path = null;
				URI uri = NativesUtils.class.getResource(libPath).toURI();
				
				FileSystem fileSystem = null;
				if ("jar".equals(uri.getScheme())) {
					// Try to get the file system if it exists.
					try {
						fileSystem = FileSystems.getFileSystem(uri);
					} catch (Exception e) {
						// For some reason, exception is thrown in either case: when 
						// there is no filesystem of that name, but also when it already
						// exists. Exception must be ignored for first attempted case.
						// (otherwise one would be logged every time)
					}

					if (fileSystem == null)
						fileSystem = FileSystems.newFileSystem(uri,
								Collections.<String, Object> emptyMap());
					path = fileSystem.getPath(libPath + "/"); // NOSONAR
				} else {
					path = Paths.get(uri);
				}

				// Copy all native libraries next to jar being executed.
				try (Stream<Path> walk = Files.walk(path, 1)) {
					for (Iterator<Path> it = walk.iterator(); it.hasNext();) {
						Path filePath = it.next();
						String fileName = filePath.getFileName().toString();
						if (!fileName.contains(".")) continue;
						extractResource(filePath.toString(),
								dst + libPath + "/" + fileName, false);
					}
				}
			} else if (isBundle(NativesUtils.class)) {
				// Set library path to new temporary location next to currently executed jar/project.
				System.setProperty("java.library.path", 
						System.getProperty("java.library.path") 
						+ File.pathSeparator + new File(".").getAbsolutePath() + "/" + libPath);

				for (Bundle bundle : context.getBundles()) {
					
					// Locate explicitly jogamp wrapper bundle. Could potentially go looking at 
					// all paths loading any native stuff with complying os and architecture getting
					// rid of this hardcoded path.
					if (!bundle.getSymbolicName().equals("org.example.repack.jogamp")) continue;
					
					Enumeration<String> paths = bundle.getEntryPaths(libPath);
					if (paths == null) continue;
					
					while (paths.hasMoreElements()) {
						String path = paths.nextElement();
						URL url = bundle.getResource(path);
						
						// Copy byte by byte in runtime root.
						try (InputStream in = url.openConnection().getInputStream()) {
							File target = new File(path);
							target.getParentFile().mkdirs();
							
							try (FileOutputStream out = new FileOutputStream(target)) {
								int nbytes = 0;
							    byte[] buffer = new byte[1024*8];
						        while ((nbytes = in.read(buffer)) != -1) {
						            out.write(buffer, 0, nbytes);
						        }
							}
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void extractResource(String src, String dst, boolean force) throws IOException {
		File dstFile = new File(dst);
		
		// If not demanding overwrite and destination file already exists, exit.
		// We want to avoid any unnecessary unpacking to mitigate cold start of 
		// the application.
		if (!force && dstFile.exists()) return;
		
		// Make necessary directory structure from parent. This operation may fail
		// due to insufficient write right on current user.
		dstFile.getParentFile().mkdirs();
		
		try (InputStream is = NativesUtils.class.getResourceAsStream(src)) {
			Files.copy(is, dstFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			dstFile.delete();
			throw e;
		} catch (NullPointerException e) {
			dstFile.delete();
			throw new FileNotFoundException("Resource " + src + " was not found inside JAR.");
		}
	}
	
	private static String getRootPath(Class<?> clazz) {
		try {
			return new File(clazz.getProtectionDomain()
					.getCodeSource().getLocation().toURI()).getPath();
		} catch (URISyntaxException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private static String getSystemDescriptor() {
		String arch = System.getProperty("os.arch");
		String os = System.getProperty("os.name").toLowerCase();
		if (os.indexOf("win") >= 0) {
			// Is Windows.
			os = "windows";
		} else if (os.indexOf("mac") >= 0) {
			// Is OSX.
			os = "macosx";
			arch = "universal";
		} else if ((os.indexOf("nix") > -1 
				 || os.indexOf("nux") > -1 
				 || os.indexOf("aix", 1) > -1 )) {
			// Is Unix.
			os = "linux";
		} else if (os.indexOf("sunos") > -1) { 
			// Is Solaris.
			os = "solaris";
		}
		return os+"-"+arch;
	}
	
	private static boolean isBundle(Class<?> clazz) {
		return clazz.getResource(clazz.getSimpleName()+".class").toString().startsWith("bundle:");
	}
	
	private static boolean isJar(Class<?> clazz) {
		return clazz.getResource(clazz.getSimpleName()+".class").toString().startsWith("jar:");
	}
}
