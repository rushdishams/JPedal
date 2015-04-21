
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
 * SwingSearchWindow.java
 * ---------------
 */
package org.jpedal.examples.viewer.gui.swing;

import java.awt.BorderLayout;

import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.jpedal.*;
import org.jpedal.display.GUIDisplay;
import org.jpedal.examples.viewer.Commands;
import org.jpedal.examples.viewer.Values;
import org.jpedal.examples.viewer.commands.Scroll;
import org.jpedal.examples.viewer.gui.generic.GUISearchWindow;
import org.jpedal.external.JPedalActionHandler;
import org.jpedal.external.Options;
import org.jpedal.grouping.DefaultSearchListener;
import org.jpedal.grouping.PdfGroupingAlgorithms;
import org.jpedal.grouping.SearchListener;
import org.jpedal.grouping.SearchType;
import org.jpedal.gui.GUIFactory;
import org.jpedal.objects.PdfPageData;
import org.jpedal.utils.LogWriter;
import org.jpedal.utils.Messages;
import org.jpedal.utils.repositories.generic.Vector_Rectangle_Int;


/**
 * Provides interactive search Window and search capabilities.
 *
 * We extend from JFrame to add search into the left side-tab or
 * to pop open a search window when either
 * style==SEARCH_EXTERNAL_WINDOW || style==SEARCH_TABBED_PANE
 * search style can be controlled from Viewer preferences.
 */
public class SwingSearchWindow extends JFrame implements GUISearchWindow{
    
    
    private Map searchAreas;
    
    private boolean backGroundSearch;
    private boolean endSearch;
    private int searchKey;
    private Thread searchThread;
    private final Runnable searchRunner = new Runnable() {
        
        @Override
        public void run() {
            
            //resultsList.setStatus(SearchList.SEARCH_INCOMPLETE);
            final boolean searchingInBackground = backGroundSearch;
            
            //Now local variable is set we can turn off global variable
            backGroundSearch = false;
            
            final int currentKey = searchKey;
            try{
                // [AWI]: Create a new list model to append the search results to
                // NOTE: This was added to prevent a deadlock issue that occurred on the
                // EDT when the search resulted in a large number of hits
                final DefaultListModel resultListModel;
                
                if(updateListDuringSearch) {
                    resultListModel = listModel;
                } else {
                    resultListModel = new DefaultListModel();
                }
                
                int start = 1;
                int end = decode_pdf.getPageCount()+1;
                
                if(singlePageSearch){
                    start = decode_pdf.getPageNumber();
                    end = start+1;
                }
                
                //Create new value as this current page could change half way through a search
                final int currentPage = commonValues.getCurrentPage();
                int page;
                boolean continueSearch = true;
                for(; start!=end; start++){
                    if(usingMenuBarSearch){
                        //When using menu bar, break from loop if result found
                        if(resultsList.getResultCount()>=1){
                            break;
                        }
                        page = currentPage+(start-1);
                        if(page>commonValues.getPageCount()) {
                            page -= commonValues.getPageCount();
                        }
                    }else{
                        page = start;
                    }
                    
                    if(searchAreas!=null){
                        final int[][] highlights = (int[][])searchAreas.get(page);
                        if(highlights!=null){
                            for(int i = highlights.length-1; i>-1; i--){
                                final int[] a = highlights[i];
                                //[AWI]: Update the search method to take the target list model as a parameter
                                continueSearch = searchPage(page, a[0], a[1], a[0]+a[2], a[1]+a[3], currentKey, resultListModel);
                            }
                        }
                    }else{
                        
                        //[AWI]: Update the search method to take the target list model as a parameter
                        continueSearch = searchPage(page, currentKey, resultListModel);
                    }
                    
                    if(!continueSearch) {
                        break;
                    }
                    
                    // new value or 16 pages elapsed
                    if (!searchingInBackground && (resultListModel.getSize()>0) | ((page % 16) == 0)) {
                        searchCount.setText(Messages.getMessage("PdfViewerSearch.ItemsFound") + ' ' + itemFoundCount + ' '
                                + Messages.getMessage("PdfViewerSearch.Scanning") + page);
                    }
                    
                }
                
                if(!searchingInBackground){
                    searchCount.setText(Messages.getMessage("PdfViewerSearch.ItemsFound") + ' ' + itemFoundCount + "  "
                            + Messages.getMessage("PdfViewerSearch.Done"));
                }
                
                if(!usingMenuBarSearch){ //MenuBarSearch freezes if we attempt to wait
                    //Wait for EDT to catch up and prevent losing results
                    while(resultListModel.size()!=itemFoundCount){
                        Thread.sleep(200);
                    }
                }
                
                // [AWI]: Update the list model displayed in the results list
                // NOTE: This was added here to address an EDT lock-up and contention issue
                // that can occur when a large result set is returned from a search. By
                // setting the model once at the end, we only take the hit for updating the
                // JList once.
                listModel = resultListModel;
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        if ( resultsList != null && listModel != null ) {
                            resultsList.setModel( listModel );
                        }
                    }
                });
                
                if(continueSearch){
                    //resultsList.setLength(listModel.capacity());
                    currentGUI.setResults(resultsList);
                    
                    resultsList.setSelectedIndex(0);
                }
                
//        if(itemFoundCount>0)
//          resultsList.setStatus(SearchList.SEARCH_COMPLETE_SUCCESSFULLY);
//        else
//          resultsList.setStatus(SearchList.NO_RESULTS_FOUND);
                
                ((PdfDecoder)decode_pdf).repaint();
                
                //switch on buttons as soon as search produces valid results
                if(!searchingInBackground){
                    currentGUI.getButtons().getButton(Commands.NEXTRESULT).setEnabled(true);
                    currentGUI.getButtons().getButton(Commands.PREVIOUSRESULT).setEnabled(true);
                }
                
                
            }catch(final Exception e){
                if(LogWriter.isOutput()) {
                    LogWriter.writeLog("Exception in handling search "+e);
                }
//        e.printStackTrace();
                if(!searchingInBackground){
                    currentGUI.getButtons().getButton(Commands.NEXTRESULT).setEnabled(true);
                    currentGUI.getButtons().getButton(Commands.PREVIOUSRESULT).setEnabled(true);
                }
                
                //resultsList.setStatus(SearchList.SEARCH_PRODUCED_ERROR);
            }
        }
    };
    
    int style;
    
    /**flag to stop multiple listeners*/
    private boolean isSetup;
    
    boolean usingMenuBarSearch;
    
    int lastPage=-1;
    
    String defaultMessage="Search PDF Here";
    
    final JProgressBar progress = new JProgressBar(0,100);
    JTextField searchText;
    JTextField searchCount;
    DefaultListModel listModel;
    SearchList resultsList;
    JLabel label;
    
    private JPanel advancedPanel;
    private JComboBox searchType;
    private JCheckBox wholeWordsOnlyBox, caseSensitiveBox, multiLineBox, highlightAll, searchAll, useRegEx, searchHighlightedOnly;
    
    @Override
    public void setWholeWords(final boolean wholeWords){
        wholeWordsOnlyBox.setSelected(wholeWords);
    }
    
    @Override
    public void setCaseSensitive(final boolean caseSensitive){
        caseSensitiveBox.setSelected(caseSensitive);
    }
    
    @Override
    public void setMultiLine(final boolean multiLine){
        multiLineBox.setSelected(multiLine);
    }
    
   // @Override
    //public void setSearchHighlightsOnly(boolean highlightOnly){
   //     searchHighlightedOnly.setSelected(highlightOnly);
   // }
    
    /**flag to show searching taking place*/
    public boolean isSearch;
    
    /**Flag to show search has happened and needs reset*/
    public boolean hasSearched;
    
    //public boolean requestInterupt=false;
    
    boolean updateListDuringSearch = true;
    
    @Override
    public void setUpdateListDuringSearch(final boolean updateListDuringSearch) {
        this.updateListDuringSearch = updateListDuringSearch;
    }
    
    JButton searchButton;
    
    /**number fo search items*/
    private int itemFoundCount;
    
    /**used when fiding text to highlight on page*/
    final Map textPages=new HashMap();
    final Map textRectangles=new HashMap();
    
    /**Current Search value*/
    String[] searchTerms = {""};
    
    /**Search this page only*/
    boolean singlePageSearch;
    
    final JPanel nav=new JPanel();
    
    Values commonValues;
    final GUIFactory currentGUI;
    PdfDecoderInt decode_pdf;
    
    int searchTypeParameters;
    
    int firstPageWithResults;
    
    /**deletes message when user starts typing*/
    private boolean deleteOnClick;
    
    public SwingSearchWindow(final GUIFactory currentGUI) {
        this.currentGUI=currentGUI;
        this.setName("searchFrame");
        
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
    }
    
    @Override
    public void init(final PdfDecoderInt dec, final Values values){
        
        this.decode_pdf = dec;
        this.commonValues = values;
        
        if(isSetup){ //global variable so do NOT reinitialise
            searchCount.setText(Messages.getMessage("PdfViewerSearch.ItemsFound")+ ' ' +itemFoundCount);
            searchText.selectAll();
            searchText.grabFocus();
        }else{
            isSetup=true;
            
            setTitle(Messages.getMessage("PdfViewerSearchGUITitle.DefaultMessage"));
            
            defaultMessage=Messages.getMessage("PdfViewerSearchGUI.DefaultMessage");
            
            searchText=new JTextField(10);
            searchText.setText(defaultMessage);
            searchText.setName("searchText");
            /*
            * [AWI] Add a focus listener to detect when keyboard input is needed in the search text field. This
            * registration was added to support systems configured with touchscreens and virtual keyboards.
            */
            searchText.addFocusListener( new SearchTextFocusListener() );
            
            searchButton=new JButton(Messages.getMessage("PdfViewerSearch.Button"));
            
            advancedPanel = new JPanel(new GridBagLayout());
            
            searchType = new JComboBox(new String[] {Messages.getMessage("PdfViewerSearch.MatchWhole"),
                Messages.getMessage("PdfViewerSearch.MatchAny")});
            
            wholeWordsOnlyBox = new JCheckBox(Messages.getMessage("PdfViewerSearch.WholeWords"));
            wholeWordsOnlyBox.setName("wholeWords");
            
            searchHighlightedOnly = new JCheckBox(Messages.getMessage("PdfViewerSearch.HighlightsOnly"));
            searchHighlightedOnly.setName("highlightsOnly");
            
            caseSensitiveBox = new JCheckBox(Messages.getMessage("PdfViewerSearch.CaseSense"));
            caseSensitiveBox.setName("caseSensitive");
            
            multiLineBox = new JCheckBox(Messages.getMessage("PdfViewerSearch.MultiLine"));
            multiLineBox.setName("multiLine");
            
            highlightAll = new JCheckBox(Messages.getMessage("PdfViewerSearch.HighlightsCheckBox"));
            highlightAll.setName("highlightAll");
            
            useRegEx = new JCheckBox(Messages.getMessage("PdfViewerSearch.RegExCheckBox"));
            useRegEx.setName("useregex");
            
            searchType.setName("combo");
            
            final GridBagConstraints c = new GridBagConstraints();
            
            advancedPanel.setPreferredSize(new Dimension(advancedPanel.getPreferredSize().width, 150));
            c.gridx = 0;
            c.gridy = 0;
            
            c.anchor = GridBagConstraints.PAGE_START;
            c.fill = GridBagConstraints.HORIZONTAL;
            
            c.weightx = 1;
            c.weighty = 0;
            advancedPanel.add(new JLabel(Messages.getMessage("PdfViewerSearch.ReturnResultsAs")), c);
            
            c.insets = new Insets(5,0,0,0);
            c.gridy = 1;
            advancedPanel.add(searchType, c);
            
            c.gridy = 2;
            advancedPanel.add(new JLabel(Messages.getMessage("PdfViewerSearch.AdditionalOptions")), c);
            
            c.insets = new Insets(0,0,0,0);
            c.weighty = 1;
            c.gridy = 3;
            advancedPanel.add(wholeWordsOnlyBox, c);
            c.weighty = 1;
            c.gridy = 4;
            advancedPanel.add(caseSensitiveBox, c);
            
            c.weighty = 1;
            c.gridy = 5;
            advancedPanel.add(multiLineBox, c);
            
            c.weighty = 1;
            c.gridy = 6;
            advancedPanel.add(highlightAll, c);
            
            c.weighty = 1;
            c.gridy = 7;
            advancedPanel.add(useRegEx, c);
            
            c.weighty = 1;
            c.gridy = 8;
            advancedPanel.add(searchHighlightedOnly, c);
            
            advancedPanel.setVisible(false);
            
            nav.setLayout(new BorderLayout());
            
            this.addWindowListener(new WindowListener(){
                @Override
                public void windowOpened(final WindowEvent arg0) {}
                
                //flush objects on close
                @Override
                public void windowClosing(final WindowEvent arg0) {
                    
                    removeSearchWindow(true);
                }
                
                @Override
                public void windowClosed(final WindowEvent arg0) {}
                
                @Override
                public void windowIconified(final WindowEvent arg0) {}
                
                @Override
                public void windowDeiconified(final WindowEvent arg0) {}
                
                @Override
                public void windowActivated(final WindowEvent arg0) {}
                
                @Override
                public void windowDeactivated(final WindowEvent arg0) {}
            });
            
            nav.add(searchButton,BorderLayout.EAST);
            
            nav.add(searchText,BorderLayout.CENTER);
            
            searchAll=new JCheckBox();
            searchAll.setSelected(true);
            searchAll.setText(Messages.getMessage("PdfViewerSearch.CheckBox"));
            
            final JPanel topPanel = new JPanel();
            topPanel.setLayout(new BorderLayout());
            topPanel.add(searchAll, BorderLayout.NORTH);
            
            label = new JLabel("<html><center> " + "Show Advanced");
            label.setForeground(Color.blue);
            label.setName("advSearch");
            
            label.addMouseListener(new MouseListener() {
                boolean isVisible;
                
                String text = "Show Advanced";
                
                @Override
                public void mouseEntered(final MouseEvent e) {
                    if(GUIDisplay.allowChangeCursor) {
                        nav.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    }
                    label.setText("<html><center><a href=" + text + '>' + text + "</a></center>");
                }
                
                @Override
                public void mouseExited(final MouseEvent e) {
                    if(GUIDisplay.allowChangeCursor) {
                        nav.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    }
                    label.setText("<html><center>" + text);
                }
                
                @Override
                public void mouseClicked(final MouseEvent e) {
                    if (isVisible) {
                        text = Messages.getMessage("PdfViewerSearch.ShowOptions");
                        label.setText("<html><center><a href=" + text + '>' + text + "</a></center>");
                        advancedPanel.setVisible(false);
                    } else {
                        text = Messages.getMessage("PdfViewerSearch.HideOptions");
                        label.setText("<html><center><a href=" + text + '>' + text + "</a></center>");
                        advancedPanel.setVisible(true);
                    }
                    
                    isVisible = !isVisible;
                }
                
                @Override
                public void mousePressed(final MouseEvent e) {}
                @Override
                public void mouseReleased(final MouseEvent e) {}
            });
            
            label.setBorder(BorderFactory.createEmptyBorder(3, 4, 4, 4));
            topPanel.add(label, BorderLayout.SOUTH);
            //      nav.
            
            nav.add(topPanel,BorderLayout.NORTH);
            itemFoundCount=0;
            textPages.clear();
            textRectangles.clear();
            listModel = null;
            
            searchCount=new JTextField(Messages.getMessage("PdfViewerSearch.ItemsFound")+ ' ' +itemFoundCount);
            searchCount.setEditable(false);
            nav.add(searchCount,BorderLayout.SOUTH);
            
            listModel = new DefaultListModel();
            resultsList=new SearchList(listModel,textPages, textRectangles);
            resultsList.setName("results");
            
                         /**
             * highlight text on item selected
             */
            
            resultsList.addListSelectionListener(new ListSelectionListener(){
                @Override
                public void valueChanged(final ListSelectionEvent e) {
                    /**
                     * Only do something on mouse button up,
                     * prevents this code being called twice
                     * on mouse click
                     */
                    if (!e.getValueIsAdjusting()) {
                        
                        if(!Values.isProcessing()){//{if (!event.getValueIsAdjusting()) {
                            
                            final float scaling=currentGUI.getScaling();
                            //int inset=currentGUI.getPDFDisplayInset();
                            
                            final int id=resultsList.getSelectedIndex();
                            
                            decode_pdf.getTextLines().clearHighlights();
                            //System.out.println("clicked pdf = "+decode_pdf.getClass().getName() + "@" + Integer.toHexString(decode_pdf.hashCode()));
                            
                            if(id!=-1){
                                
                                final Integer key= id;
                                final Object newPage=textPages.get(key);
                                
                                if(newPage!=null){
                                    final int nextPage= (Integer) newPage;
                                    
                                    
                                    //move to new page
                                    if(commonValues.getCurrentPage()!=nextPage){
                                        
                                        commonValues.setCurrentPage(nextPage);
                                        
                                        currentGUI.resetStatusMessage(Messages.getMessage("PdfViewer.LoadingPage")+ ' ' +commonValues.getCurrentPage());
                                        
                                        /**reset as rotation may change!*/
                                        decode_pdf.setPageParameters(scaling, commonValues.getCurrentPage());
                                        
                                        //decode the page
                                        currentGUI.decodePage();
                                        
                                        ((PdfDecoder)decode_pdf).invalidate();
                                    }
                                    
                                    while(Values.isProcessing()){
                                        //Ensure page has been processed else highlight may be incorrect
                                        try {
                                            Thread.sleep(500);
                                        } catch (final InterruptedException ee) {
                                            ee.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                        }
                                    }
                                    
                                    
                                    
                                    /**
                                     * Highlight all search results on page.
                                     */
                                    if((searchTypeParameters & SearchType.HIGHLIGHT_ALL_RESULTS)== SearchType.HIGHLIGHT_ALL_RESULTS){
                                        
                                        //                    PdfHighlights.clearAllHighlights(decode_pdf);
                                        int[][] showAllOnPage;
                                        Vector_Rectangle_Int storageVector = new Vector_Rectangle_Int();
                                        int lastPage = -1;
                                        for(int k=0; k!=resultsList.getModel().getSize(); k++){
                                            final Object page=textPages.get(k);
                                            
                                            if(page!=null){
                                                
                                                final int currentPage = (Integer) page;
                                                if(currentPage!=lastPage){
                                                    storageVector.trim();
                                                    showAllOnPage = storageVector.get();
                                                    
                                                    for(int p=0; p!=showAllOnPage.length; p++){
                                                        System.out.println(Arrays.toString(showAllOnPage[p]));
                                                    }
                                                    
                                                    decode_pdf.getTextLines().addHighlights(showAllOnPage, true, lastPage);
                                                    lastPage = currentPage;
                                                    storageVector = new Vector_Rectangle_Int();
                                                }
                                                
                                                final Object highlight= textRectangles.get(k);
                                                
                                                if(highlight instanceof int[]){
                                                    storageVector.addElement((int[])highlight);
                                                }
                                                if(highlight instanceof int[][]){
                                                    final int[][] areas = (int[][])highlight;
                                                    for(int i=0; i!=areas.length; i++){
                                                        storageVector.addElement(areas[i]);
                                                    }
                                                }
                                                //decode_pdf.addToHighlightAreas(decode_pdf, storageVector, currentPage);
                                                //                        }
                                            }
                                        }
                                        storageVector.trim();
                                        showAllOnPage = storageVector.get();
                                        
                                        decode_pdf.getTextLines().addHighlights(showAllOnPage, true, lastPage);
                                    }else{
                                        //                    PdfHighlights.clearAllHighlights(decode_pdf);
                                        final Object page=textPages.get(key);
                                        final int currentPage = (Integer) page;
                                        
                                        final Vector_Rectangle_Int storageVector = new Vector_Rectangle_Int();
                                        int[] scroll = null;
                                        final Object highlight= textRectangles.get(key);
                                        if(highlight instanceof int[]){
                                            storageVector.addElement((int[])highlight);
                                            scroll=(int[])highlight;
                                        }
                                        
                                        if(highlight instanceof int[][]){
                                            final int[][] areas = (int[][])highlight;
                                            scroll=areas[0];
                                            for(int i=0; i!=areas.length; i++){
                                                storageVector.addElement(areas[i]);
                                            }
                                        }
                                        
                                        if(scroll!=null){
                                            Scroll.rectToHighlight(scroll,currentPage, decode_pdf);
                                        }
                                        storageVector.trim();
                                        decode_pdf.getTextLines().addHighlights(storageVector.get(), true, currentPage);
                                        //PdfHighlights.addToHighlightAreas(decode_pdf, storageVector, currentPage);
                                        
                                    }
                                    
                                    
                                    //Refresh display after clearing highlights
                                    decode_pdf.repaintPane(0);
                                    decode_pdf.getPages().refreshDisplay();
                                }
                            }
                        }
                        
                        //When page changes make sure only relevant navigation buttons are displayed
                        if(commonValues.getCurrentPage()==1) {
                            currentGUI.getButtons().setBackNavigationButtonsEnabled(false);
                        } else {
                            currentGUI.getButtons().setBackNavigationButtonsEnabled(true);
                        }
                        
                        if(commonValues.getCurrentPage()==decode_pdf.getPageCount()) {
                            currentGUI.getButtons().setForwardNavigationButtonsEnabled(false);
                        } else {
                            currentGUI.getButtons().setForwardNavigationButtonsEnabled(true);
                        }
                        
                        
                    }else{
                        resultsList.repaint();
                        
                    }
                }
            });
            resultsList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
            
            //setup searching
            searchButton.addActionListener(new ActionListener(){
                @Override
                public void actionPerformed(final ActionEvent e) {
                    
                    if(!isSearch){
                        
                        try {
                            searchTypeParameters = SearchType.DEFAULT;
                            
                            if(wholeWordsOnlyBox.isSelected()) {
                                searchTypeParameters |= SearchType.WHOLE_WORDS_ONLY;
                            }
                            
                            if(caseSensitiveBox.isSelected()) {
                                searchTypeParameters |= SearchType.CASE_SENSITIVE;
                            }
                            
                            if(multiLineBox.isSelected()) {
                                searchTypeParameters |= SearchType.MUTLI_LINE_RESULTS;
                            }
                            
                            if(highlightAll.isSelected()) {
                                searchTypeParameters |= SearchType.HIGHLIGHT_ALL_RESULTS;
                            }
                            
                            if(useRegEx.isSelected()) {
                                searchTypeParameters |= SearchType.USE_REGULAR_EXPRESSIONS;
                            }
                            
                            if(searchHighlightedOnly.isSelected()) {
                                searchTypeParameters |= SearchType.SEARCH_HIGHLIGHTS_ONLY;
                            }
                            
                            final String textToFind = searchText.getText().trim();
                            if(searchType.getSelectedIndex() == 0){ // find exact word or phrase
                                searchTerms = new String[] { textToFind };
                            } else { // match any of the words
                                searchTerms = textToFind.split(" ");
                                for (int i = 0; i < searchTerms.length; i++) {
                                    searchTerms[i] = searchTerms[i].trim();
                                }
                            }
                            
                            singlePageSearch = !searchAll.isSelected();
                            
                            searchText();
                        } catch (final Exception e1) {
                            e1.printStackTrace();
                        }
                    }else{
                        //requestInterupt = true;
                        //searcher.interrupt();
                        isSearch=false;
                        searchButton.setText(Messages.getMessage("PdfViewerSearch.Button"));
                    }
                    decode_pdf.requestFocus();
                }
            });
            
            searchText.selectAll();
            deleteOnClick=true;
            
            searchText.addKeyListener(new KeyListener(){
                @Override
                public void keyTyped(final KeyEvent e) {
                    
                    final int id = e.getID();
                    if (id == KeyEvent.KEY_TYPED) {
                        final char key=e.getKeyChar();
                        
                        if(key=='\n'){
                            
                            if(!decode_pdf.isOpen()){
                                currentGUI.showMessageDialog("File must be open before you can search.");
                            }else{
                                try {
                                    
                                    isSearch=false;
                                    searchTypeParameters = SearchType.DEFAULT;
                                    
                                    if(wholeWordsOnlyBox.isSelected()) {
                                        searchTypeParameters |= SearchType.WHOLE_WORDS_ONLY;
                                    }
                                    
                                    if(caseSensitiveBox.isSelected()) {
                                        searchTypeParameters |= SearchType.CASE_SENSITIVE;
                                    }
                                    
                                    if(multiLineBox.isSelected()) {
                                        searchTypeParameters |= SearchType.MUTLI_LINE_RESULTS;
                                    }
                                    
                                    if(highlightAll.isSelected()) {
                                        searchTypeParameters |= SearchType.HIGHLIGHT_ALL_RESULTS;
                                    }
                                    
                                    if(useRegEx.isSelected()) {
                                        searchTypeParameters |= SearchType.USE_REGULAR_EXPRESSIONS;
                                    }
                                    
                                    if(searchHighlightedOnly.isSelected()) {
                                        searchTypeParameters |= SearchType.SEARCH_HIGHLIGHTS_ONLY;
                                    }
                                    
                                    final String textToFind = searchText.getText().trim();
                                    if(searchType.getSelectedIndex() == 0){ // find exact word or phrase
                                        searchTerms = new String[] { textToFind };
                                    } else { // match any of the words
                                        searchTerms = textToFind.split(" ");
                                        for (int i = 0; i < searchTerms.length; i++) {
                                            searchTerms[i] = searchTerms[i].trim();
                                        }
                                    }
                                    
                                    singlePageSearch = !searchAll.isSelected();
                                    
                                    
                                    searchText();
                                    
                                    decode_pdf.requestFocus();
                                } catch (final Exception e1) {
                                    e1.printStackTrace();
                                }
                            }
                        }
                    }
                }
                
                @Override
                public void keyPressed(final KeyEvent arg0) {}
                
                @Override
                public void keyReleased(final KeyEvent arg0) {
                    
                    currentGUI.getButtons().getButton(Commands.NEXTRESULT).setEnabled(false);
                    currentGUI.getButtons().getButton(Commands.PREVIOUSRESULT).setEnabled(false);
                }
            });
            
            searchText.addFocusListener(new FocusListener() {
                
                @Override
                public void focusLost(final FocusEvent e) {
                    if(searchText.getText().isEmpty()){
                        searchText.setText(defaultMessage);
                        deleteOnClick=true;
                    }
                }
                
                @Override
                public void focusGained(final FocusEvent e) {
                    //clear when user types
                    if(deleteOnClick){
                        deleteOnClick=false;
                        searchText.setText("");
                    }
                }
            });
            
            if(style==SEARCH_EXTERNAL_WINDOW || style==SEARCH_TABBED_PANE){
                //build frame
                final JScrollPane scrollPane=new JScrollPane();
                scrollPane.getViewport().add(resultsList);
                scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
                scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                scrollPane.getVerticalScrollBar().setUnitIncrement(80);
                scrollPane.getHorizontalScrollBar().setUnitIncrement(80);
                
                getContentPane().setLayout(new BorderLayout());
                getContentPane().add(scrollPane,BorderLayout.CENTER);
                getContentPane().add(nav,BorderLayout.NORTH);
                getContentPane().add(advancedPanel, BorderLayout.SOUTH);
                
                //position and size
                Container frame = (Container)currentGUI.getFrame();
                if((commonValues.getModeOfOperation() == Values.RUNNING_APPLET) &&
                    (currentGUI.getFrame() instanceof JFrame)) {
                        frame = ((JFrame) currentGUI.getFrame()).getContentPane();
                    }
                
                if(style==SEARCH_EXTERNAL_WINDOW){
                    final int w=230;
                    
                    final int h=frame.getHeight();
                    final int x1=frame.getLocationOnScreen().x;
                    int x=frame.getWidth()+x1;
                    final int y=frame.getLocationOnScreen().y;
                    final Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
                    
                    final int width = d.width;
                    if(x+w>width){
                        x=width-w;
                        frame.setSize(x-x1,frame.getHeight());
                    }
                    
                    setSize(w,h);
                    setLocation(x,y);
                }
                searchAll.setFocusable(false);
                
                searchText.grabFocus();
                
            }else{
                //Whole Panel not used, take what is needed
                currentGUI.setSearchText(searchText);
            }
        }
        
    }
    
    /**
     * find text on page withSwingWindow
     */
    @Override
    public void findWithoutWindow(final PdfDecoderInt dec, final Values values, final int searchType, final boolean listOfTerms, final boolean singlePageOnly, final String searchValue){
        
        if(!isSearch){
            backGroundSearch = true;
            isSearch=true;
            
            this.decode_pdf = dec;
            this.commonValues = values;
            
            ((PdfDecoder)decode_pdf).setLayout(new BorderLayout());
            ((PdfDecoder)decode_pdf).add(progress, BorderLayout.SOUTH);
            progress.setValue(0);
            progress.setMaximum(commonValues.getPageCount());
            progress.setVisible(true);
            ((PdfDecoder)decode_pdf).validate();

            if(!listOfTerms){ // find exact word or phrase
                searchTerms = new String[] {searchValue};
            } else { // match any of the words
                searchTerms = searchValue.split(" ");
                for (int i = 0; i < searchTerms.length; i++) {
                    searchTerms[i] = searchTerms[i].trim();
                }
            }
            
            searchTypeParameters = searchType;
            
            singlePageSearch = singlePageOnly;
            
            find(dec, values);
            
        }else{
            currentGUI.showMessageDialog("Please wait for search to finish before starting another.");
        }
    }
    
    /**
     * find text on page
     */
    @Override
    public void find(final PdfDecoderInt dec, final Values values){
        
        
        //    System.out.println("clicked pdf = "+decode_pdf.getClass().getName() + "@" + Integer.toHexString(decode_pdf.hashCode()));
        
        /**
         * pop up new window to search text (initialise if required
         */
        if(!backGroundSearch){
            init(dec, values);
            if(style==SEARCH_EXTERNAL_WINDOW) {
                setVisible(true);
            }
        }else{
            try {
                searchText();
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
        
    }
    
    
    @Override
    public void removeSearchWindow(final boolean justHide) {
        
        setVisible(false);
        
        if (isSetup && !justHide) {
            if (listModel != null) {
                listModel.clear();
            }
            
            itemFoundCount = 0;
            isSearch = false;
            
        }
        
        //lose any highlights and force redraw with non-existent box
        if (decode_pdf != null) {
            decode_pdf.getTextLines().clearHighlights();
            ((PdfDecoder) decode_pdf).repaint();
        }
    }
    
    /**
     * Clears the current set of results from the result list
     */
    private void clearCurrentResults(){
        listModel.clear();
        resultsList.clearSelection();
        textPages.clear();
        textRectangles.clear();
        
        itemFoundCount = 0;
        decode_pdf.getTextLines().clearHighlights();
        
        //Refresh display after clearing highlights
        decode_pdf.repaintPane(0);
        decode_pdf.getPages().refreshDisplay();
        
    }
    
    private void searchText() throws Exception {
        
        //Flag is we are using menu bar search
        usingMenuBarSearch = style == SEARCH_MENU_BAR;
        
        //Alter searchKey so the update thread knows not to update
        searchKey++;
        
        //Reset last page searched flag.
        lastPage=-1;
        
        /*
        * To prevent the chance of hitting the maximum value of searchKey
        * we should reset long after a value large enough to guarantee
        * any thread using a searchKey of 0 is closed.
        */
        if(searchKey>3000) {
            searchKey = 0;
        }
        
        //Cancel a search if currently exists
        if(searchThread!=null && searchThread.isAlive()){
            
            //Call for search to finish
            endSearch = true;
            
            searchThread.interrupt();
            
            while (searchThread.isAlive()) {
                //Wait for search to end
                try {
                    Thread.sleep(5000);
                } catch (Exception e) {
                    if (LogWriter.isOutput()) {
                        LogWriter.writeLog("Attempting to set propeties values " + e);
                    }
                }
            }

            endSearch = false;
            
        }
        
        if(!usingMenuBarSearch && (searchTypeParameters & SearchType.SEARCH_HIGHLIGHTS_ONLY) == SearchType.SEARCH_HIGHLIGHTS_ONLY){
            searchAreas = decode_pdf.getTextLines().getAllHighlights();
        }else{
            searchAreas = null;
        }
        
        clearCurrentResults();
        
        searchThread = new Thread(searchRunner);
        searchThread.setDaemon(true);
        searchThread.start();
    }
    
    /**
     * Performs the currently set up search on the given page
     * @param page :: Page to be searched with the currently set term and settings
     * @param currentKey :: The current search key, used to control results update when search ends
     * @param model :: [AWI] The list model to append the search results to
     * @return True if search routine should continue
     */
    private boolean searchPage(final int page, final int currentKey, final DefaultListModel model) throws Exception{
        final PdfPageData currentPageData = decode_pdf.getPdfPageData();
        final int x1 = currentPageData.getMediaBoxX(page);
        final int x2 = currentPageData.getMediaBoxWidth(page) + x1;
        final int y2 = currentPageData.getMediaBoxY(page);
        final int y1 = currentPageData.getMediaBoxHeight(page) + y2;
        return searchPage(page, x1, y1, x2, y2, currentKey, model);
    }
    /**
     * Performs the currently set up search on the given page
     * @param x1 the left x cord
     * @param y1 the upper y cord
     * @param x2 the right x cord
     * @param y2 the lower y cord
     * @param page :: Page to be searched with the currently set term and settings
     * @param currentKey :: The current search key, used to control results update when search ends
     * @param model :: [AWI] The list model to append the search results to
     * @return True if search routine should continue
     */
    private boolean searchPage(final int page, final int x1, final int y1, final int x2, final int y2, final int currentKey, final DefaultListModel model) throws Exception{
        
        final PdfGroupingAlgorithms grouping;
        
        final PdfPageData pageSize = decode_pdf.getPdfPageData();
        
        if (page == commonValues.getCurrentPage()) {
            grouping = decode_pdf.getGroupingObject();
        } else {
            decode_pdf.decodePageInBackground(page);
            grouping = decode_pdf.getBackgroundGroupingObject();
        }
        
//    // set size
//    int x1 = pageSize.getCropBoxX(page);
//    int x2 = pageSize.getCropBoxWidth(page);
//    int y1 = pageSize.getCropBoxY(page);
//    int y2 = pageSize.getCropBoxHeight(page);
        
        final SearchListener listener = new DefaultSearchListener();
        
        // tell JPedal we want teasers
        grouping.generateTeasers();
        
        //allow us to add options
        grouping.setIncludeHTML(true);
        
        //Set search term in results list
        StringBuilder term = new StringBuilder();
        for(int i=0; i!=searchTerms.length; i++){
            term.append(searchTerms[i]).append(' ');
        }
        resultsList.setSearchTerm(term.toString());
        
        final SortedMap highlightsWithTeasers = grouping.findTextWithinInAreaWithTeasers(x1, y1, x2, y2, pageSize.getRotation(page), page, searchTerms, searchTypeParameters, listener);
        
        /**
         * update data structures with results from this page
         */
        if (!highlightsWithTeasers.isEmpty()) {
            
            itemFoundCount += highlightsWithTeasers.size();
            
            for (final Object o : highlightsWithTeasers.entrySet()) {
                final Map.Entry e = (Map.Entry) o;
                
                /*highlight is a rectangle or a rectangle[]*/
                final Object highlight = e.getKey();
                
                final String teaser = (String) e.getValue();
                
                // [AWI]: Only push the results off to the EDT if the model is the
                // current active list model.
                if (!SwingUtilities.isEventDispatchThread() && model == listModel) {
                    final Runnable setTextRun = new Runnable() {
                        
                        @Override
                        public void run() {
                            if(currentKey==searchKey){
                                //if highights ensure displayed by wrapping in tags
                                if (!teaser.contains("<b>")) {
                                    model.addElement(teaser);
                                } else {
                                    model.addElement("<html>" + teaser + "</html>");
                                }
                            }
                        }
                    };
                    SwingUtilities.invokeLater(setTextRun);
                    
                } else {
                    if (!teaser.contains("<b>")) {
                        model.addElement(teaser);
                    } else {
                        model.addElement("<html>" + teaser + "</html>");
                    }
                }
                
                final Integer key = textRectangles.size();
                textRectangles.put(key, highlight);
                textPages.put(key, page);
            }
            
        }
        
        lastPage = page;
        
        //Ending search now
        return !endSearch;
        
    }
    
    @Override
    public int getFirstPageWithResults() {
        return firstPageWithResults;
    }
    
    @Override
    public void grabFocusInInput() {
        searchText.grabFocus();
        
    }
    
    @Override
    public boolean isSearchVisible() {
        return this.isVisible();
    }
    
    @Override
    public void setViewStyle(final int style) {
        this.style = style;
    }
    
    @Override
    public int getViewStyle() {
        return style;
    }
    
    @Override
    public void setSearchText(final String s) {
        deleteOnClick = false;
        searchText.setText(s);
    }
    
    @Override
    public Map getTextRectangles() {
        return Collections.unmodifiableMap(textRectangles);
    }
    
    @Override
    public SearchList getResults() {
        
        return resultsList;
    }
    
    @Override
    public SearchList getResults(final int page) {
        
        usingMenuBarSearch = style==SEARCH_MENU_BAR;
        
        if(page !=lastPage && usingMenuBarSearch){
            
            try {
                
                searchKey++;
                if(searchKey>3000) {
                    searchKey = 0;
                }
                
                clearCurrentResults();
                
                // [AWI]: Updated for changes to searchPage method
                searchPage(page, searchKey, listModel);
                
            } catch (final Exception e) {
                e.printStackTrace();
            }
            
        }
        
        return resultsList;
    }
    
    /**
     * Reset search text and menu bar buttons when opening new page
     */
    @Override
    public void resetSearchWindow(){
        if(isSetup){
            
            searchText.setText(defaultMessage);
            deleteOnClick=true;
            
            // [AWI] Reset the result status text label (itemFoundCount should be reset already
            // by the removeSearchWindow method)
            searchCount.setText(Messages.getMessage("PdfViewerSearch.ItemsFound")+ ' ' +itemFoundCount);
            
            if(hasSearched){
                //          resultsList = null;
                currentGUI.getButtons().getButton(Commands.NEXTRESULT).setEnabled(false);
                currentGUI.getButtons().getButton(Commands.PREVIOUSRESULT).setEnabled(false);
                hasSearched = false;
            }
            decode_pdf.requestFocus();
        }
    }
    
    /**
     * [AWI] Focus listener used to detect when the search text field has been given focus.</p>
     *
     * This class was added to support detecting when Keyboard input is needed for systems using a touchscreen monitor
     * with a virtual keyboard.
     */
    class SearchTextFocusListener implements FocusListener {
        @Override
        public void focusGained( final FocusEvent e ) {
            if ( decode_pdf != null ) {
                final Object handler = decode_pdf.getExternalHandler( Options.KeyboardReadyHandler );
                if (handler instanceof JPedalActionHandler) {
                    ((JPedalActionHandler)handler).actionPerformed( currentGUI, null );
                }
            }
        }
        @Override
        public void focusLost( final FocusEvent e ) {
            // No action
        }
    }
}