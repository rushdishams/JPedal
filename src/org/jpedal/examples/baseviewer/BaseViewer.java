
/*
 * ===========================================
 * Java Pdf Extraction Decoding Access Library
 * ===========================================
 *
 * Project Info:  http://www.idrsolutions.com
 * Help section for developers at http://www.idrsolutions.com/support/
 *
 * (C) Copyright 1997-2015 IDRsolutions and Contributors.
 *
 * This file is part of JPedal/JPDF2HTML5
 *

 *
 * ---------------
 * BaseViewer.java
 * ---------------
 */

package org.jpedal.examples.baseviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.jpedal.display.Display;
import org.jpedal.PdfDecoder;
import org.jpedal.examples.viewer.Viewer;
import org.jpedal.exception.PdfException;
import org.jpedal.objects.PdfPageData;

/** <h2><b>BaseViewer</b></h2>
 * 
 * <p>If you are compiling, you will need to download all the examples source files from :
 * <a href="http://www.idrsolutions.com/how-to-view-pdf-files-in-java/">How to View PDF File in Java.</a></p>
 *
 * <p><b>Run directly from jar with java -cp jpedal.jar org/jpedal/examples/baseviewer/BaseViewer</b></p>
 *
 * <p>There are plenty of tutorials on how to configure the Viewer on our website <a href="http://www.idrsolutions.com/java-pdf-library-support/">Support Section.</a></p>
 * 
 * <p>This class provides example code to create a bare-bones PDF Viewer in JavaFX.</p>
 * 
 * <p><a href="http://files.idrsolutions.com/samplecode/org/jpedal/examples/baseviewer/BaseViewerFX.java.html">Click here for a bare-bones PDF Viewer in JavaFX.</a></p>
 * 
 * <p><a href="http://www.idrsolutions.com/java-pdf-library-support/http://www.idrsolutions.com/java-pdf-library-support/">For more help and tutorials, visit our websites Support Section.</a></p>
 * 
 */
public class BaseViewer {

	/**
	 * Enum to control how we fit the content to the page.
	 * 
	 * AUTO will automatically fit the content to the stage depending on its orientation
	 * WIDTH will fit the content to the stage width depending on its orientation
	 * HEIGHT will fit the content to the stage height depending on its orientation
	 */
	public enum FitToPage{
		AUTO, WIDTH, HEIGHT
	}

	final JFrame mainFrame = new JFrame("Base Viewer - " + PdfDecoder.version);

	JScrollPane scrollPdf = new JScrollPane();

	final JPanel insets = new JPanel();

	final PdfDecoder pdf = new PdfDecoder();
	int currentPage = 1;
	float scale = 1.0f;

	private final float[] scalings = {0.01f, 0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 4.0f, 7.5f, 10.0f};

	private static final int insetV = 25;

	private static final int insetH = 25;

	private int currentScaling;

	final JPanel top = new JPanel();

	final JToolBar toolBar = new JToolBar("Tool Bar");
	final JButton open = new JButton("Open");
	final JButton back = new JButton("Back");
	final JButton forward = new JButton("Forward");
	final JButton zoomIn = new JButton("Zoom in");
	final JButton zoomOut = new JButton("Zoom out");
	final JButton fitWidth = new JButton("Fit to Width");
	final JButton fitHeight = new JButton("Fit to Height");
	final JButton fitPage = new JButton("Fit to Page");

	final JLabel pageCounter = new JLabel("Page 0 of 0");

	/**
	 *
	 * @param args is of type String[]
	 */
	public static void main(final String[] args) {

		final int argLength=args.length;

		/*switch(argLength){
            case 0:
                new BaseViewer();
                break;

            case 1:
                new BaseViewer(args[0]);
                break;

            default:
                System.out.println("You need 0 or 1 parameters");
                break;
        }*/
		//new BaseViewer("csr2014-supplement-en.pdf");
		new BaseViewer("01.pdf");
		// new BaseViewer();
	}

	public BaseViewer(final String value){



		if(value != null) {
			try {

				pdf.openPdfFile(value);
				decodePage();

			} catch (final PdfException ex) {
				ex.printStackTrace();
			}
		}else {
			openFile();
		}
		init();
	}

	public BaseViewer(){

		init();

		openFile();
	}


	private void init(){
		//  pdf.setBackground(Color.RED);


		PdfDecoder.init(true);


		if (SwingUtilities.isEventDispatchThread()) {

			pdf.setDisplayView(Display.SINGLE_PAGE, Display.DISPLAY_CENTERED);


		} else {
			final Runnable doPaintComponent = new Runnable() {

				@Override
				public void run() {
					pdf.setDisplayView(Display.SINGLE_PAGE, Display.DISPLAY_CENTERED);

				}
			};

			SwingUtilities.invokeLater(doPaintComponent);

		}
		//pdf.setDisplayView(Display.SINGLE_PAGE,Display.DISPLAY_CENTERED);

		open.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				openFile();
			}
		});

		back.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				if(currentPage>1) {
					currentPage--;
				}

				decodePage();

			}
		});

		forward.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				if(currentPage<pdf.getPageCount()) {
					currentPage++;
				}

				decodePage();

			}
		});

		currentScaling = 5;

		zoomIn.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				//zoom in
				System.out.println("Zoom in");

				if(currentScaling < scalings.length-1) {

					currentScaling = closestIndex(scale, scalings);

					if(scale >= scalings[closestIndex(scale, scalings)]) {

						currentScaling++;

					}

					System.out.println("Current Scale ======= "+currentScaling);

					scale = scalings[currentScaling];

				}

				pdf.setPageParameters(scale, currentPage);

				pdf.updateUI();

				System.out.println("Scaling =========== "+pdf.getScaling());
			}
		});

		zoomOut.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				//zoom out
				System.out.println("Zoom out");

				if(currentScaling > 0) {

					currentScaling = closestIndex(scale, scalings);

					if(scale <= scalings[closestIndex(scale, scalings)]) {

						currentScaling--;

					}

					System.out.println("Current Scale ======= "+currentScaling);

					scale = scalings[currentScaling];

				}

				pdf.setPageParameters(scale, currentPage);

				pdf.updateUI();

				System.out.println("Scaling =========== "+pdf.getScaling());
			}
		});

		fitWidth.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				// Fit to height
				System.out.println("Fit to Width");

				fitToX(FitToPage.WIDTH);

			}
		});

		fitHeight.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				// Fit to height
				System.out.println("Fit to Height");

				fitToX(FitToPage.HEIGHT);

			}
		});

		fitPage.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				// Fit to height
				System.out.println("Fit to Page");

				fitToX(FitToPage.AUTO);

			}
		});

		mainFrame.addComponentListener(new ComponentListener() {

			@Override
			public void componentResized(final ComponentEvent e) {
			}

			@Override
			public void componentMoved(final ComponentEvent e) {
			}

			@Override
			public void componentShown(final ComponentEvent e) {
			}

			@Override
			public void componentHidden(final ComponentEvent e) {
			}

		});

		toolBar.add(open);
		toolBar.add(back);
		toolBar.add(forward);
		toolBar.add(zoomIn);
		toolBar.add(zoomOut);
		toolBar.add(fitWidth);
		toolBar.add(fitHeight);
		toolBar.add(fitPage);
		toolBar.setFloatable(false);

		top.setLayout(new GridLayout(1, 1));
		top.add(toolBar);
		((FlowLayout)insets.getLayout()).setVgap(0);
		((FlowLayout)insets.getLayout()).setHgap(0);

		insets.add(pdf);
		if(insetV > 0 || insetH > 0) {

			insets.setBorder(new EmptyBorder(insetH, insetV, insetH, insetV));

		}
		scrollPdf = new JScrollPane(insets);
		scrollPdf.getVerticalScrollBar().setUnitIncrement(32);
		scrollPdf.getHorizontalScrollBar().setUnitIncrement(32);

		mainFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		mainFrame.getContentPane().add(top, BorderLayout.NORTH);
		//    mainFrame.getContentPane().add(pdf, BorderLayout.CENTER);
		mainFrame.getContentPane().add(scrollPdf, BorderLayout.CENTER);
		mainFrame.getContentPane().add(pageCounter, BorderLayout.SOUTH);
		mainFrame.setSize(800, 600);
		//make viewer open in the center of the screen
		mainFrame.setLocationRelativeTo(null); 
		mainFrame.addWindowListener(new WindowListener() {
			@Override
			public void windowOpened(final WindowEvent e) {}
			@Override
			public void windowIconified(final WindowEvent e) {}
			@Override
			public void windowDeiconified(final WindowEvent e) {}
			@Override
			public void windowDeactivated(final WindowEvent e) {}
			@Override
			public void windowClosing(final WindowEvent e) {}
			@Override
			public void windowClosed(final WindowEvent e) {}
			@Override
			public void windowActivated(final WindowEvent e) {}
		});

		mainFrame.setVisible(true);
	}

	public void openFile(){
		try {
			final JFileChooser jfc = new JFileChooser();
			jfc.showOpenDialog(mainFrame);

			final File f = jfc.getSelectedFile();

			if(f!=null){

				pdf.openPdfFile(f.getAbsolutePath());

				decodePage();
			}
		} catch (final PdfException e) {
			e.printStackTrace();
		}

	}

	private void fitToX(final FitToPage fitToPage) {

		//Handle how we fit the content to the page
		switch(fitToPage){

		case AUTO:
			if(pdf.getPDFWidth()<pdf.getPDFHeight()) {
				fitToX(FitToPage.HEIGHT);
			}
			else {
				fitToX(FitToPage.WIDTH);
			}

			break;

		case WIDTH:
			final float pageW=pdf.getPdfPageData().getCropBoxWidth2D(currentPage);
			scale = ((float)(scrollPdf.getViewportBorderBounds().getWidth()-insetV-insetV)/pageW);
			System.out.println("SCALE ==== "+ scale);
			pdf.setPageParameters(scale, currentPage);

			break;

		case HEIGHT:
			final float pageH=pdf.getPdfPageData().getCropBoxHeight2D(currentPage);
			scale = ((float)(scrollPdf.getViewportBorderBounds().getHeight()-insetH-insetH)/pageH);
			pdf.setPageParameters(scale, currentPage);

			break;

		}

		pdf.updateUI();
	}

	private static int closestIndex(final float scale, final float[] scalings) {
		float currentMinDiff = Float.MAX_VALUE;
		int closest = 0;

		for(int i = 0; i < scalings.length - 1; i++) {

			final float diff = Math.abs(scalings[i] - scale);

			if(diff < currentMinDiff) {
				currentMinDiff = diff;
				closest = i;
			}

		}
		return closest;
	}


	private void decodePage(){


		try {

			pageCounter.setText("Page "+currentPage+" of "+pdf.getPageCount());
			//My edits
			//Rectangle rectangle = new Rectangle(190,412,100,100);
			//int [][]rectangle = {{308,361, 100,100}};
			int [][]rectangle = {{791,598, 50 ,10}};

			//pdf.getTextLines().addHighlights(new Rectangle[]{rectangle}, false, currentPage);
			pdf.getTextLines().addHighlights(rectangle, true, 1);
			//pdf.repaint();

			final PdfPageData pageData = pdf.getPdfPageData();
			final int inset=10;
			final int cw;
			final int ch;
			final int rotation=pageData.getRotation(currentPage);
			if(rotation==90 || rotation==270){
				cw = pageData.getCropBoxHeight(currentPage);
				ch = pageData.getCropBoxWidth(currentPage);
			}else{
				cw = pageData.getCropBoxWidth(currentPage);
				ch = pageData.getCropBoxHeight(currentPage);
			}

			//define pdf view width and height
			final float width = (float) (pdf.getWidth()-inset-inset);
			final float height = (float) (pdf.getHeight()-inset-inset);

			if((width>0)&&(height>0)){
				final float x_factor;
				final float y_factor;
				x_factor = width / cw;
				y_factor = height / ch;

				if(x_factor<y_factor) {
					scale = x_factor;
				} else {
					scale = y_factor;
				}
			}




			pdf.setPageParameters(scale, currentPage);


			pdf.decodePage(currentPage);


			//wait to ensure decoded
			//	pdf.waitForDecodingToFinish();



			pdf.invalidate();
			pdf.updateUI();
			pdf.validate();

		}catch (final Exception e) {
			e.printStackTrace();
		}
	}
}