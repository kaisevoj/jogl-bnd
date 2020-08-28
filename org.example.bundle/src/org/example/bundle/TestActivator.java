package org.example.bundle;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

import javax.swing.JFrame;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;

import jogamp.opengl.awt.Java2D;

public class TestActivator 
		implements BundleActivator {
		
	private Thread thread = null;

	private JFrame frame = null;
	
	public static void main(String[] args) throws Exception {
		// run as java application works
		new TestActivator().start(null);
	}
	
	@Override
	public void start(BundleContext context) throws Exception {
		System.out.println("starting test activator");

		if (context != null) {
			Bundle jogamp = null;
			for (Bundle bundle : context.getBundles()) {
				if (!bundle.getSymbolicName().equals("org.example.repack.jogamp")) continue;
				jogamp = bundle;
				break;
			}
	
			// sun.java2d.opengl.OGLUtilities is in java 8 JRE/JDK, not in 10 <=
			
			URL[] urls = new URL[] {new File("C:\\Program Files\\Java\\jre1.8.0_191\\lib\\rt.jar").toURI().toURL()};
			
			ClassLoader j2dCL = new Java2D().getClass().getClassLoader();
			ClassLoader thisCL = this.getClass().getClassLoader();
			ClassLoader bundleCL = jogamp.getClass().getClassLoader();
			ClassLoader systemCL = ClassLoader.getSystemClassLoader();
			ClassLoader childCL = new URLClassLoader(urls, j2dCL);
			
			System.out.println("this "+thisCL); // org.eclipse.osgi.internal.loader.EquinoxClassLoader@64c87930[org.example.bundle:1.0.0.SNAPSHOT(id=1)]
			System.out.println("bundle "+bundleCL); // aQute.launcher.pre.EmbeddedLauncher$Loader
			System.out.println("system "+systemCL); // sun.misc.Launcher$AppClassLoader
			System.out.println("j2d "+j2dCL); // org.eclipse.osgi.internal.loader.EquinoxClassLoader[org.example.repack.jogamp:2.3.2.SNAPSHOT(id=4)]
			System.out.println("j2d p "+j2dCL.getParent());
			if (j2dCL.getParent() != null) System.out.println("j2d p2 "+j2dCL.getParent().getParent());
			
			Class<?> classToLoad = Class.forName("sun.java2d.opengl.OGLUtilities", true, childCL);
			System.out.println(classToLoad); // class sun.java2d.opengl.OGLUtilities
			System.out.println("Java version: "+Runtime.class.getPackage().getImplementationVersion()); // Java version: 1.8.0_191
			NativesUtils.init(context);
		}		

		//System.exit(0);//XXX
		
		thread = new Thread(() -> {
			try {
				Thread.sleep(1000);
				frame = new HelloWorld().build();
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		
		thread.start();
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		System.out.println("stopping test activator");
		
		if (thread != null) {
			thread.interrupt();
			while (thread.isAlive());
			thread = null;
		}
		if (frame != null) {
			frame.dispose();
			frame = null;
		}
	}
	
	private static class HelloWorld implements GLEventListener {

		@Override
		public void init(GLAutoDrawable arg0) {}

		@Override
		public void display(GLAutoDrawable drawable) {
			final GL2 gl = drawable.getGL().getGL2();
			
			// Draw H
			gl.glBegin(GL2.GL_LINES);
			gl.glVertex2d(-0.8, 0.6);
			gl.glVertex2d(-0.8, -0.6);
			gl.glVertex2d(-0.8, 0.0);
			gl.glVertex2d(-0.4, 0.0);
			gl.glVertex2d(-0.4, 0.6);
			gl.glVertex2d(-0.4, -0.6);
			gl.glEnd();
			
			// Draw W
			gl.glBegin(GL2.GL_LINES);
			gl.glVertex2d(0.4, 0.6);
			gl.glVertex2d(0.4, -0.6);
			gl.glVertex2d(0.4, -0.6);
			gl.glVertex2d(0.6, 0);
			gl.glVertex2d(0.6, 0);
			gl.glVertex2d(0.8, -0.6);
			gl.glVertex2d(0.8, -0.6);
			gl.glVertex2d(0.8, 0.6);
			gl.glEnd();
		}

		@Override
		public void reshape(GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4) {}

		@Override
		public void dispose(GLAutoDrawable arg0) {}

		public JFrame build() {
			final GLProfile gp = GLProfile.get(GLProfile.GL2);
			GLCapabilities cap = new GLCapabilities(gp);

			final GLCanvas gc = new GLCanvas(cap);
			HelloWorld sq = new HelloWorld();
			gc.addGLEventListener(sq);
			gc.setSize(400, 400);

			final JFrame frame = new JFrame("Hello World");
			frame.add(gc);
			frame.setSize(500, 400);
			frame.setVisible(true);
			return frame;
		}
	}
} 

