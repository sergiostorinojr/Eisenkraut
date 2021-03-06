/*
 *  TimelineScroll.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 *
 *
 *  Changelog:
 *		12-May-05	re-created from de.sciss.meloncillo.timeline.TimelineScroll
 *		15-Jul-05	fix in setPosition to avoid duplicate event generation
 */

package de.sciss.eisenkraut.timeline;

import de.sciss.app.AbstractApplication;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.eisenkraut.gui.GraphicsUtil;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.util.PrefsUtil;
import de.sciss.io.Span;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

/**
 *  A GUI element for allowing
 *  horizontal timeline scrolling.
 *  Subclasses <code>JScrollBar</code>
 *  simply to override the <code>paintComponent</code>
 *  method: an additional hairline is drawn
 *  to visualize the current timeline position.
 *  also a translucent hoverState rectangle is drawn
 *  to show the current timeline selection.
 *	<p>
 *	This class tracks the catch preferences
 *
 *  TODO: the display properties work well
 *				with the Aqua look+and+feel, however
 *				are slightly wrong on Linux with platinum look+feel
 *				because the scroll gadgets have different positions.
 */
@SuppressWarnings("serial")
public class TimelineScroll
        extends JScrollBar
        implements AdjustmentListener, TimelineListener, DynamicListening, PreferenceChangeListener {

    public static final int 	TYPE_UNKNOWN		= 0;
    public static final int 	TYPE_DRAG			= 1;
    public static final int 	TYPE_TRANSPORT		= 2;

    private final Session   	doc;

    private Dimension			recentSize			= getMinimumSize();
    private Shape				shpSelection		= null;
    private Shape				shpPosition     	= null;
    private Span				timelineSel			= null;
    private long				timelineLen			= 0;
    private int					timelineLenShift	= 0;
    private long				timelinePos			= 0;
    private Span				timelineVis			= new Span();
    private boolean				prefCatch;

    private final	Object		adjustmentSource	= new Object();
    
    private final Color	colrSelection   	= GraphicsUtil.colrSelection();
    private static final Color	colrPosition    	= Color.red;
    private static final Stroke	strkPosition    	= new BasicStroke( 0.5f );

    private final int			trackMarginLeft;
    private final int			trackMargin;

    private boolean				wasAdjusting		= false;
    private boolean				adjustCatchBypass	= false;
    private int					catchBypassCount	= 0;
    private boolean				catchBypassWasSynced= false;

    /**
     *  Constructs a new <code>TimelineScroll</code> object.
     *	TODO: a clean way to determine the track rectangle ...
     *
     *  @param  doc		session Session
     */
    public TimelineScroll(Session doc) {
        super(HORIZONTAL);
        this.doc = doc;

        LookAndFeel laf = UIManager.getLookAndFeel();
        if( (laf != null) && laf.isNativeLookAndFeel() && (laf.getName().toLowerCase().contains("aqua")) ) {
            trackMarginLeft = 6;  // for Aqua look and feel
            trackMargin		= 39;
        } else {
            trackMarginLeft = 16;	// works for Metal, Motif, Liquid, Metouia
            trackMargin		= 32;
        }

        timelineLen = doc.timeline.getLength();
        timelineVis = doc.timeline.getVisibleSpan();
        for (timelineLenShift = 0; (timelineLen >> timelineLenShift) > 0x3FFFFFFF; timelineLenShift++) ;
        recalculateTransforms();
        recalculateBoundedRange();

        // --- Listener ---

        new DynamicAncestorAdapter(this).addTo(this);
        this.addAdjustmentListener(this);

        new DynamicAncestorAdapter(new DynamicPrefChangeManager(AbstractApplication.getApplication().getUserPrefs(),
                new String[]{PrefsUtil.KEY_CATCH}, this)).addTo(this);

        setFocusable(false);    // XXX TODO -- doesn't have effect with WebLaF ?
    }

    /**
     *  Paints the normal scroll bar using
     *  the super class's method. Additionally
     *  paints timeline position and selection cues
     */
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
 
        Dimension   d           = getSize();
        Graphics2D  g2          = (Graphics2D) g;
        Stroke		strkOrig	= g2.getStroke();
        Paint		pntOrig		= g2.getPaint();

        if (d.width != recentSize.width || d.height != recentSize.height) {
            recentSize = d;
            recalculateTransforms();
        }

        if (shpSelection != null) {
            g2.setColor(colrSelection);
            g2.fill(shpSelection);
        }
        if (shpPosition != null) {
            g2.setColor(colrPosition);
            g2.setStroke(strkPosition);
            g2.draw(shpPosition);
        }

        g2.setStroke(strkOrig);
        g2.setPaint(pntOrig);
    }

    private void recalculateBoundedRange() {
        final int len  = (int) (timelineLen >> timelineLenShift);
        final int len2 = (int) (timelineVis.getLength() >> timelineLenShift);
        if (len > 0) {
            if (!isEnabled()) setEnabled(true);
            setValues((int) (timelineVis.getStart() >> timelineLenShift), len2, 0, len);   // val, extent, min, max
            setUnitIncrement(Math.max(1, (len2 >> 5)));             // 1/32 extent
            setBlockIncrement(Math.max(1, ((len2 * 3) >> 2)));      // 3/4 extent
        } else {
            if (isEnabled()) setEnabled(false);
            setValues(0, 100, 0, 100);    // full view will hide the scrollbar knob
        }
    }

    /*
     *  Calculates virtual->screen coordinates
     *  for timeline position and selection
     */
    private void recalculateTransforms() {
        double  scale, x;

        if (timelineLen > 0) {
            scale = (double) (recentSize.width - trackMargin) / (double) timelineLen;
            if (timelineSel != null) {
                shpSelection = new Rectangle2D.Double(timelineSel.getStart() * scale + trackMarginLeft, 0,
                        timelineSel.getLength() * scale, recentSize.height);
            } else {
                shpSelection = null;
            }
            x = timelinePos * scale + trackMarginLeft;
            shpPosition = new Line2D.Double(x, 0, x, recentSize.height);
        } else {
            shpSelection = null;
            shpPosition  = null;
        }
    }
    
    /**
     *  Updates the red hairline representing
     *  the current timeline position in the
     *  overall timeline span.
     *  Called directly from TimelineFrame
     *  to improve performance. Don't use
     *  elsewhere.
     *
     *  @param  pos			new position in absolute frames
     *  @param  patience	allowed graphic update interval
     *
     *  @see	java.awt.Component#repaint( long )
     */
    public void setPosition(long pos, long patience, int type) {
        if( prefCatch && (catchBypassCount == 0) /* && timelineVis.contains( timelinePos ) */ &&
            ((timelineVis.stop != timelineLen) || (pos < timelineVis.start)) &&
            !timelineVis.contains( pos + (type == TYPE_TRANSPORT ? timelineVis.getLength() >> 3 : 0) )) {

            timelinePos = pos;
            long		start;
            final long	stop;

            start = timelinePos;
            if (type == TYPE_TRANSPORT) {
                start -= timelineVis.getLength() >> 3;
            } else if (type == TYPE_DRAG) {
                if (timelineVis.getStop() <= timelinePos) {
                    start -= timelineVis.getLength();
                }
            } else {
                start -= timelineVis.getLength() >> 2;
            }
            stop    = Math.min(timelineLen, Math.max(0, start) + timelineVis.getLength());
            start   = Math.max(0, stop - timelineVis.getLength());

            if (stop > start) {
                // it's crucial to update internal var timelineVis here because
                // otherwise the delay between emitting the edit and receiving the
                // change via timelineScrolled might be two big, causing setPosition
                // to fire more than one edit!
                timelineVis = new Span(start, stop);
                doc.timeline.editScroll(this, timelineVis);
                return;
            }
        }
        timelinePos = pos;
        recalculateTransforms();
        repaint(patience);
    }

    public void addCatchBypass() {
        if (++catchBypassCount == 1) {
            catchBypassWasSynced = timelineVis.contains(timelinePos);
        }
    }

    public void removeCatchBypass() {
        if ((--catchBypassCount == 0) && catchBypassWasSynced) {
            catchBypassWasSynced = false;
            if (prefCatch && !timelineVis.contains(timelinePos)) {
                long		start;
                final long	stop;

                start   = timelinePos - (timelineVis.getLength() >> 2);
                stop    = Math.min(timelineLen, Math.max(0, start) + timelineVis.getLength());
                start   = Math.max(0, stop - timelineVis.getLength());
                if (stop > start) {
                    // it's crucial to update internal var timelineVis here because
                    // otherwise the delay between emitting the edit and receiving the
                    // change via timelineScrolled might be two big, causing setPosition
                    // to fire more than one edit!
                    timelineVis = new Span(start, stop);
                    doc.timeline.editScroll(this, timelineVis);
                }
            }
        }
    }

// ---------------- DynamicListening interface ---------------- 

    public void startListening() {
        doc.timeline.addTimelineListener(this);
        recalculateTransforms();
        repaint();
    }

    public void stopListening() {
        doc.timeline.removeTimelineListener(this);
    }

// ---------------- PreferenceChangeListener interface ---------------- 

    public void preferenceChange(PreferenceChangeEvent e) {
        final String key    = e.getKey();
        final String value  = e.getNewValue();

        if (!key.equals(PrefsUtil.KEY_CATCH)) return;

        prefCatch = Boolean.valueOf(value);
        if (!prefCatch) return;

        catchBypassCount	= 0;
        adjustCatchBypass	= false;
        if (!(timelineVis.contains(timelinePos))) {
            long start      = Math.max(0, timelinePos - (timelineVis.getLength() >> 2));
            final long stop = Math.min(timelineLen, start + timelineVis.getLength());
            start = Math.max(0, stop - timelineVis.getLength());
            if (stop > start) {
                doc.timeline.editScroll(this, new Span(start, stop));
            }
        }
    }

// ---------------- TimelineListener interface ---------------- 

    public void timelineSelected(TimelineEvent e) {
        timelineSel = doc.timeline.getSelectionSpan();
        recalculateTransforms();
        repaint();
    }

    public void timelineChanged(TimelineEvent e) {
        timelineLen = doc.timeline.getLength();
        timelineVis = doc.timeline.getVisibleSpan();
        for (timelineLenShift = 0; (timelineLen >> timelineLenShift) > 0x3FFFFFFF; timelineLenShift++) ;
        recalculateTransforms();
        recalculateBoundedRange();
        repaint();
    }

    // ignored since the timeline frame will inform us
    public void timelinePositioned(TimelineEvent e) { /* ignore */ }

    public void timelineScrolled(TimelineEvent e) {
        timelineVis = doc.timeline.getVisibleSpan();
        if (e.getSource() != adjustmentSource) {
            recalculateBoundedRange();
        }
    }

// ---------------- AdjustmentListener interface ---------------- 
// we're listening to ourselves

    public void adjustmentValueChanged(AdjustmentEvent e) {
        if (!isEnabled()) return;

        final boolean	isAdjusting	= e.getValueIsAdjusting();

        final Span		oldVisi		= doc.timeline.getVisibleSpan();
        final Span		newVisi		= new Span( this.getValue() << timelineLenShift,
                                        (this.getValue() + this.getVisibleAmount()) << timelineLenShift );

        if( prefCatch && isAdjusting && !wasAdjusting ) {
            adjustCatchBypass = true;
            addCatchBypass();
        } else if( wasAdjusting && !isAdjusting && adjustCatchBypass ) {
            if( prefCatch && !newVisi.contains( timelinePos )) {
                // we need to set prefCatch here even though laterInvocation will handle it,
                // because removeCatchBypass might look at it!
                prefCatch = false;
                AbstractApplication.getApplication().getUserPrefs().putBoolean( PrefsUtil.KEY_CATCH, false );
            }
            adjustCatchBypass = false;
            removeCatchBypass();
        }

        if( !newVisi.equals( oldVisi )) {
//			if( prefCatch && oldVisi.contains( timelinePos ) && !newVisi.contains( timelinePos )) {
//				AbstractApplication.getApplication().getUserPrefs().putBoolean( PrefsUtil.KEY_CATCH, false );
//			}
            doc.timeline.editScroll( adjustmentSource, newVisi );
        }

        wasAdjusting	= isAdjusting;
    }
}