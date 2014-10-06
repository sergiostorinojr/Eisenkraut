/*
 *  GroupSync.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut.net;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.sciss.net.OSCBundle;
import de.sciss.util.Disposable;

public class GroupSync
implements Disposable
{
	private final List	collSlaves	= new ArrayList();
	protected boolean	active		= false;

	public GroupSync()
	{
		 /* empty */ 
	}

	public void addSlave( Slave s )
	{
		collSlaves.add( s );
	}
	
	public void removeSlave( Slave s )
	{
		collSlaves.remove( s );
	}
	
	public boolean isActive()
	{
		return active;
	}
	
	public void activate( OSCBundle bndl )
	{
		active = true;
		for( Iterator iter = collSlaves.iterator(); iter.hasNext(); ) {
			((Slave) iter.next()).groupActivate( bndl );
		}
	}
	
	public void deactivate( OSCBundle bndl )
	{
		active = false;
		for( Iterator iter = collSlaves.iterator(); iter.hasNext(); ) {
			((Slave) iter.next()).groupDeactivate( bndl );
		}
	}
	
	// -------------- Disposable interface --------------

	public void dispose()
	{
		collSlaves.clear();
		active = false;
	}

	// -------------- internal interfaces --------------
	
	public static interface Slave
	{
		public void groupActivate( OSCBundle bndl );
		public void groupDeactivate( OSCBundle bndl );
	}
}
