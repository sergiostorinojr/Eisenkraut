/*
 *  DecimatedTrail.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.io;

import de.sciss.app.BasicEvent;
import de.sciss.app.EventManager;
import de.sciss.common.ProcessingThread;
import de.sciss.eisenkraut.math.MathUtil;
import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.IOUtil;
import de.sciss.io.Span;
import de.sciss.timebased.BasicTrail;
import de.sciss.timebased.Stake;

import javax.swing.*;
import javax.swing.undo.CompoundEdit;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class DecimatedTrail extends BasicTrail {

    protected static final boolean	DEBUG					= false;

    protected int					SUBNUM;
    protected int					MAXSHIFT;
    protected int					MAXCOARSE;
    protected long					MAXMASK;
    protected int					MAXCEILADD;

    public static final int			MODEL_PCM				= 0;
    public static final int			MODEL_HALFWAVE_PEAKRMS	= 1;
    public static final int			MODEL_MEDIAN			= 2;
    public static final int			MODEL_FULLWAVE_PEAKRMS	= 3;
    public static final int			MODEL_SONA				= 10;

    protected int					modelChannels;
    protected int					decimChannels;
    protected int					fullChannels;
    protected int					model;

    protected AudioFile[]			tempF					= null; // lazy
    protected DecimationHelp[]		decimHelps;
    protected AudioTrail			fullScale;

    protected float[][]				tmpBuf					= null; // lazy
    protected int					tmpBufSize;
    protected float[][]				tmpBuf2					= null; // lazy
    protected int					tmpBufSize2;

    protected static final Paint	pntBusyDark;
    protected static final Paint	pntBusyLight;

    private static final int[] busyPixelsDark = {
            0xFF343434, 0xFF3F3F3F, 0xFF575757,
            0xFF191919, 0xFF4D4D4D, 0xFF353535,
            0xFF4E4E4E, 0xFF2A2A2A, 0xFF3F3F3F
    };

    private static final int[] busyPixelsLight = {
            0xFFCBCBCB, 0xFFC0C0C0, 0xFFA8A8A8,
            0xFFE6E6E6, 0xFFB2B2B2, 0xFFCACACA,
            0xFFB1B1B1, 0xFFD5D5D5, 0xFFC0C0C0
    };

    protected final Object			bufSync					= new Object();
    protected final Object			fileSync				= new Object();

    protected final List<Span> drawBusyList			= new ArrayList<Span>();

    protected Thread				threadAsync				= null;
    protected AudioFile[]			tempFAsync				= null; // lazy
    protected volatile boolean		keepAsyncRunning		= false;

    protected EventManager			asyncManager			= null;

    protected static final double	TWENTYBYLOG10			= 20 / MathUtil.LN10; // 8.685889638065;
    protected static final double	TENBYLOG10				= 10 / MathUtil.LN10;

    static {
        final BufferedImage imgDark = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
        imgDark.setRGB(0, 0, 3, 3, busyPixelsDark, 0, 3);
        pntBusyDark = new TexturePaint(imgDark, new Rectangle(0, 0, 3, 3));
        final BufferedImage imgLight = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
        imgLight.setRGB(0, 0, 3, 3, busyPixelsLight, 0, 3);
        pntBusyLight = new TexturePaint(imgLight, new Rectangle(0, 0, 3, 3));
    }

    protected final boolean isDark = UIManager.getBoolean("dark-skin");

    protected final Paint pntBusy = isDark ? pntBusyDark : pntBusyLight;

    protected DecimatedTrail() {
        super();
    }

    protected BasicTrail createEmptyCopy()
    {
        throw new IllegalStateException( "Not allowed" );
    }

    public final int getDefaultTouchMode() { return TOUCH_SPLIT; }
    public final int getChannelNum() { return decimChannels; }
    public final int getNumModelChannels() { return modelChannels; }
    public final int getNumDecimations() { return SUBNUM; }
    public final int getModel() { return model; }

    public abstract DecimationInfo getBestSubsample( Span tag, int minLen );

    protected static void setProgression(long len, double progWeight)
            throws ProcessingThread.CancelledException {
        ProcessingThread.update((float) (len * progWeight));
    }

    protected static void flushProgression() {
        ProcessingThread.flushProgression();
    }

    protected final void killAsyncThread() {
        if (threadAsync != null) {
            synchronized (threadAsync) {
                if (threadAsync.isAlive()) {
                    keepAsyncRunning = false;
                    try {
                        threadAsync.wait();
                    } catch (InterruptedException e1) {
                        System.err.println("DecimatedTrail - killAsyncThread:");
                        e1.printStackTrace();
                    }
                }
            }
        }
    }

    protected void removeAllDep(Object source, List<Stake> stakes, CompoundEdit ce, Span union) {
        if (DEBUG) System.err.println("removeAllDep " + union.toString());
        throw new IllegalArgumentException("n.y.i.");
    }

    protected abstract File[] createCacheFileNames();

    protected int[][] createCacheChannelMaps()
    {
        final int[][] fullChanMaps	= fullScale.getChannelMaps();
        final int[][] cacheChanMaps	= new int[ fullChanMaps.length ][];

        for( int i = 0; i < fullChanMaps.length; i++ ) {
//			System.out.println( "fullChanMaps[ " + i + " ] = " );
//			for( int k = 0; k < fullChanMaps[ i ].length; k++ ) {
//				System.out.println( "  " + fullChanMaps[ i ][ k ]);
//			}
            cacheChanMaps[ i ] = new int[ fullChanMaps[ i ].length * modelChannels ];
            for( int j = 0; j < cacheChanMaps[ i ].length; j++ ) {
                cacheChanMaps[ i ][ j ] = j;
            }
//			System.out.println( "cacheChanMaps[ " + i + " ] = " );
//			for( int k = 0; k < cacheChanMaps[ i ].length; k++ ) {
//				System.out.println( "  " + cacheChanMaps[ i ][ k ]);
//			}
        }

        return cacheChanMaps;
    }

    // @synchronization caller must have sync on fileSync !!!
    protected DecimatedStake alloc(Span span)
            throws IOException {

        if (!Thread.holdsLock(fileSync)) throw new IllegalMonitorStateException();

        final long floorStart = span.start & MAXMASK;
        final long ceilStop = (span.stop + MAXCEILADD) & MAXMASK;
        final Span extSpan = (floorStart == span.start) && (ceilStop == span.stop) ? span : new Span(floorStart,
                ceilStop);
        final Span[] fileSpans = new Span[SUBNUM];
        final Span[] biasedSpans = new Span[SUBNUM];
        long fileStart;
        long fileStop;

        if (tempF == null) {
            tempF = createTempFiles(); // XXX sync
        }
        synchronized (tempF) {
            for (int i = 0; i < SUBNUM; i++) {
                fileStart = tempF[i].getFrameNum();
                fileStop = fileStart + (extSpan.getLength() >> decimHelps[i].shift);
                tempF[i].setFrameNum(fileStop);
                fileSpans[i] = new Span(fileStart, fileStop);
                biasedSpans[i] = extSpan;
            }
        }
        return new DecimatedStake( extSpan, tempF, fileSpans, biasedSpans, decimHelps );
    }

    // @synchronization caller must have sync on fileSync !!!
    protected DecimatedStake allocAsync(Span span)
            throws IOException {
        if (!Thread.holdsLock(fileSync)) throw new IllegalMonitorStateException();

        final long floorStart	= span.start & MAXMASK;
        final long ceilStop		= (span.stop + MAXCEILADD) & MAXMASK;
        final Span extSpan		= (floorStart == span.start) && (ceilStop == span.stop) ?
                                    span : new Span( floorStart, ceilStop );
        final Span[] fileSpans	= new Span[ SUBNUM ];
        final Span[] biasedSpans = new Span[ SUBNUM ];
        long fileStart;
        long fileStop;

        if (tempFAsync == null) {
            // XXX THIS IS THE PLACE TO OPEN WAVEFORM CACHE FILE
            tempFAsync = createTempFiles();
        }
        synchronized (tempFAsync) {
            for (int i = 0; i < SUBNUM; i++) {
                fileStart = tempFAsync[i].getFrameNum();
                fileStop = fileStart + (extSpan.getLength() >> decimHelps[i].shift);
                tempFAsync[i].setFrameNum(fileStop);
                fileSpans[i] = new Span(fileStart, fileStop);
                biasedSpans[i] = extSpan;
            }
        }
        return new DecimatedStake(extSpan, tempFAsync, fileSpans, biasedSpans, decimHelps);
    }

    protected AudioFile[] createTempFiles()
    throws IOException
    {
        // simply use an AIFC file with float format as temp file
        final AudioFileDescr proto	= new AudioFileDescr();
        final AudioFile[] tempFiles	= new AudioFile[ SUBNUM ];
        AudioFileDescr afd;
        proto.type					= AudioFileDescr.TYPE_AIFF;
        proto.channels				= decimChannels;
        proto.bitsPerSample			= 32;
        proto.sampleFormat			= AudioFileDescr.FORMAT_FLOAT;
        // proto.bitsPerSample = 8;
        // proto.sampleFormat = AudioFileDescr.FORMAT_INT;
        try {
            for( int i = 0; i < SUBNUM; i++ ) {
                afd				= new AudioFileDescr( proto );
                afd.file		= IOUtil.createTempFile();
                afd.rate		= decimHelps[ i ].rate;
                tempFiles[ i ]	= AudioFile.openAsWrite( afd );
            }
            return tempFiles;
        } catch( IOException e1 ) {
            for( int i = 0; i < SUBNUM; i++ ) {
                if( tempFiles[ i ] != null ) tempFiles[ i ].cleanUp();
            }
            throw e1;
        }
    }

    protected void deleteTempFiles(AudioFile[] tempFiles) {
        for (AudioFile tempFile : tempFiles) {
            if (tempFile != null) {
                tempFile.cleanUp();
                tempFile.getFile().delete();
            }
        }
    }

    protected void freeTempFiles()
    {
        synchronized( fileSync ) {
            if( tempF != null ) {
                deleteTempFiles( tempF );
            }
            // XXX THIS IS THE PLACE TO KEEP WAVEFORM CACHE FILE
            if( tempFAsync != null ) {
                deleteTempFiles( tempFAsync );
            }
        }
    }

    /*
     * @synchronization call within synchronoized( bufSync ) block
     */
    protected void createBuffers()
    {
        if( !Thread.holdsLock( bufSync )) throw new IllegalMonitorStateException();

        if( tmpBuf == null ) {

//System.out.println( "tmpBuf  : [ " + fullChannels + " ][ " + tmpBufSize + " ]" );
//System.out.println( "tmpBuf2 : [ " + decimChannels + " ][ " + tmpBufSize2 + " ]" );

            tmpBuf	= new float[ fullChannels  ][ tmpBufSize ];
            tmpBuf2	= new float[ decimChannels ][ tmpBufSize2 ];
        }
    }

    protected void freeBuffers()
    {
        synchronized( bufSync ) {
            tmpBuf	= null;
            tmpBuf2	= null;
        }
    }

    // ----------- dependant implementation -----------

    public void dispose()
    {
//System.err.println( "DecimatedTrail.dispose() " + this );
        killAsyncThread(); // this has to be the first step
        fullScale.removeDependant( this );
        freeBuffers();
        freeTempFiles();
        super.dispose();
    }

    public final boolean isBusy()
    {
        return( (threadAsync != null) && threadAsync.isAlive() );
    }

    public final void addAsyncListener( AsyncListener l )
    {
        if( !isBusy() ) {
            l.asyncFinished( new AsyncEvent( this, AsyncEvent.FINISHED, System.currentTimeMillis(), this ));
            return;
        }
        if( asyncManager == null ) {
            asyncManager = new EventManager( new EventManager.Processor() {
                public void processEvent( BasicEvent e ) {
                    final AsyncEvent	ae = (AsyncEvent) e;
                    AsyncListener		al;

                    for( int i = 0; i < asyncManager.countListeners(); i++ ) {
                        al = (AsyncListener) asyncManager.getListener( i );
                        switch( e.getID() ) {
                        case AsyncEvent.UPDATE:
                            al.asyncUpdate( ae );
                            break;
                        case AsyncEvent.FINISHED:
                            al.asyncFinished( ae );
                            break;
                        default:
                            assert false : e.getID();
                            break;
                        }
                    }
                }
            });
        }
        asyncManager.addListener( l );
    }

    public final void removeAsyncListener( AsyncListener l )
    {
        if( asyncManager != null ) asyncManager.removeListener( l );
    }

    // ---------------------- internal classes and interfaces ----------------------

    public static interface AsyncListener {
        public void asyncFinished( AsyncEvent e );
        public void asyncUpdate( AsyncEvent e );
    }

    @SuppressWarnings("serial")
    public static class AsyncEvent
            extends BasicEvent {
        protected static final int UPDATE = 0;
        protected static final int FINISHED = 1;

        private final DecimatedTrail t;

        protected AsyncEvent( Object source, int id, long when, DecimatedTrail t )
        {
            super( source, id, when );
            this.t	= t;
        }

        public DecimatedTrail getDecimatedTrail() { return t; }

        public boolean incorporate(BasicEvent oldEvent) {
            return (oldEvent instanceof AsyncEvent) &&
                    (this.getSource() == oldEvent.getSource()) &&
                    (this.getID() == oldEvent.getID()) &&
                    (t == ((AsyncEvent) oldEvent).t);
        }
    }
}