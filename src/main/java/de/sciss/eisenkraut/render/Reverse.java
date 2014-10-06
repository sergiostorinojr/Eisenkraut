/*
 *  Reverse.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 *		15-Jul-05	created
 */

package de.sciss.eisenkraut.render;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.sciss.io.Span;
import de.sciss.timebased.MarkerStake;

public class Reverse
extends AbstractRenderPlugIn
//implements RandomAccessRequester
{
//	private static final int BLOCKSIZE	= 1024;
	
	private Span			prTotalSpan;
	private RenderConsumer	prConsumer;
//	private Span			prNextSpan;

	public int getMarkerPolicy()
	{
		return POLICY_MODIFY;
	}

	public int getUnselectedAudioPolicy()
	{
		return POLICY_MODIFY;
	}

	public boolean producerBegin( RenderSource source )
	throws IOException
	{
		// random read access not necessary any more since the consumer
		// automatically handles random write access!
//		source.context.setOption( RenderContext.KEY_PREFBLOCKSIZE, new Integer( BLOCKSIZE ));
//		source.context.setOption( RenderContext.KEY_RANDOMACCESS, this );
		prTotalSpan	= source.context.getTimeSpan();
		// request last block
//		prNextSpan	= new Span( Math.max( prTotalSpan.start, prTotalSpan.stop - BLOCKSIZE ), prTotalSpan.stop );
		prConsumer	= source.context.getConsumer();
		
		// flip markers at once
		if( source.validMarkers ) {
			final int			numMarkers	= source.markers.getNumStakes();
			final List			collNew		= new ArrayList( numMarkers );
			MarkerStake			m;
			for( int i = 0; i < numMarkers; i++ ) {
				m = (MarkerStake) source.markers.get( i, true );
//				if( m.pos > prTotalSpan.start ) {	// don't mirror to prTotalSpan.stop ?
					collNew.add( m.replaceStart( prTotalSpan.stop - m.pos + prTotalSpan.start ));
//				}
			}
			source.markers.clear( this );
			source.markers.addAll( this, collNew );
		}
		
		return prConsumer.consumerBegin( source );
	}

	public boolean producerRender( RenderSource source )
	throws IOException
	{
		float	temp;
		float[]	convBuf;
	
		// reverse each block
		for( int ch = 0; ch < source.numAudioChannels; ch++ ) {
			if( !source.audioTrackMap[ ch ]) continue;
			convBuf = source.audioBlockBuf[ ch ];
			for( int i = source.audioBlockBufOff, j = i + source.audioBlockBufLen - 1; i < j; i++, j-- ) {
				temp			= convBuf[ i ];
				convBuf[ i ]	= convBuf[ j ];
				convBuf[ j ]	= temp;
			}
		}
//		// request previous block
//		prNextSpan = new Span( Math.max( prTotalSpan.start, prNextSpan.start - BLOCKSIZE ), prNextSpan.start );

		// pseudo code:
		// blockStart --> totalStop - (blockStart - totalStart) - blockLen
		// blockStart --> totalStop + totalStart - blockStop
		// shift = totalStop + totalStart - blockStop - blockStart
		source.blockSpan = source.blockSpan.shift( prTotalSpan.stop + prTotalSpan.start - source.blockSpan.stop - source.blockSpan.start );
		
		return prConsumer.consumerRender( source );
	}

	public String getName()
	{
		return getResourceString( "plugInReverse" );
	}
	
	// ---------- RandomAccessRequester interface ----------
	
//	public Span getNextSpan()
//	{
//		return prNextSpan;
//	}
}
