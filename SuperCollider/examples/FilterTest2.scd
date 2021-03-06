// last mod: 19-May-08

(
e = Eisenkraut.default;
e.connect;
fork { e.initTree; e.initSwing };
)

// Example of a realtime plug-in:
// adding reverberation (GVerb2).
//
// WARNING : scsynth must be booted
// before executing this code!
//
// After executing this code,
// a new menu item appears in
// Process -> SuperCollider.
// Choosing this item will execute
// the populateWindowFunc function
// below.
//
// WARNING : needs some realtime
// memory, so you should make sure
// to put Preferences -> Audio -> Advanced ->
// Realtime Memory to at least 32 MB.
(
var desktopFolder;
desktopFolder = "~/Desktop".standardizePath ++ "/";
x = EisKPlugIn.new.name_( "GVerb Filter" );
e.addProcessPlugIn( x );
fork { e.initTree; };

x.populateWindowFunc = { arg plug, win;
	var flow, gui, synth, ggBypass, ggRender, ggProgress, roomSize = 30, revTime = 3, outDamping = 0.5, inDamping = 0.5, dryLevel = 0.dbamp, earlyLevel = -6.dbamp, tailLevel = -12.dbamp, revTimeSpec, inDampingSpec, outDampingSpec, roomSizeSpec, levelSpec, threshSpec, lbDryLevel, lbEarlyLevel, lbTailLevel, lbOutDamping, lbRevTime, lbRoomSize, lbInDamping, lbThreshold, fStop, fCreateDef, maxRoomSize = 1000, spread = 15 /* XXX fixed for now */;

try {
	gui = GUI.current;
	win.bounds_( win.bounds.resizeTo( 400, 240 ));
	
	flow = FlowLayout( win.view.bounds );
	win.view.decorator = flow;
	
	roomSizeSpec = ControlSpec( 1, maxRoomSize, 'exp' );
	gui.staticText.new( win, Rect( 0, 0, 80, 24 ))
		.align_( \right )
		.string_( "Room Size" );
	gui.slider.new( win, Rect( 0, 0, 240, 20 ))
		.value_( roomSizeSpec.unmap( roomSize ))
		.action_({ arg b;
			roomSize = roomSizeSpec.map( b.value );
			lbRoomSize.string = roomSizeSpec.map( b.value ).round( 0.1 ).asString ++ " m²";
//			synth.set( \roomSize, roomSize );
			"WARNING: roomSize cannot be modified in realtime.\nPress bypass twice to make the change effective".postln;
		});
	lbRoomSize = gui.staticText.new( win, Rect( 0, 0, 48, 24 ))
		.string_( "30 m²" );
	flow.nextLine;

	revTimeSpec = ControlSpec( 0.1, 100, 'exp' );
	gui.staticText.new( win, Rect( 0, 0, 80, 24 ))
		.align_( \right )
		.string_( "Reverb Time" );
	gui.slider.new( win, Rect( 0, 0, 240, 20 ))
		.value_( revTimeSpec.unmap( revTime ))
		.action_({ arg b;
			revTime = revTimeSpec.map( b.value );
			lbRevTime.string = revTimeSpec.map( b.value ).round( 0.1 ).asString ++ " s";
			synth.set( \revTime, revTime );
		});
	lbRevTime = gui.staticText.new( win, Rect( 0, 0, 48, 24 ))
		.string_( "3 s" );
	flow.nextLine;

	inDampingSpec = ControlSpec( 0.0, 1.0, 'lin' );
	gui.staticText.new( win, Rect( 0, 0, 80, 24 ))
		.align_( \right )
		.string_( "Input Damping" );
	gui.slider.new( win, Rect( 0, 0, 240, 20 ))
		.value_( inDampingSpec.unmap( inDamping ))
		.action_({ arg b;
			inDamping = inDampingSpec.map( b.value );
			lbInDamping.string = (inDampingSpec.map( b.value ).round( 0.01 ) * 100).asString ++ "%";
			synth.set( \inDamping, inDamping );
		});
	lbInDamping = gui.staticText.new( win, Rect( 0, 0, 48, 24 ))
		.string_( "50%" );
	flow.nextLine;

	outDampingSpec = ControlSpec( 0.0, 1.0, 'lin' );
	gui.staticText.new( win, Rect( 0, 0, 80, 24 ))
		.align_( \right )
		.string_( "Output Damp." );
	gui.slider.new( win, Rect( 0, 0, 240, 20 ))
		.value_( outDampingSpec.unmap( outDamping ))
		.action_({ arg b;
			outDamping = outDampingSpec.map( b.value );
			lbOutDamping.string = (outDampingSpec.map( b.value ).round( 0.01 ) * 100).asString ++ "%";
			synth.set( \outDamping, outDamping );
		});
	lbOutDamping = gui.staticText.new( win, Rect( 0, 0, 48, 24 ))
		.string_( "50%" );
	flow.nextLine;
	
	levelSpec = ControlSpec( -inf, 0, 'db' );
	gui.staticText.new( win, Rect( 0, 0, 80, 24 ))
		.align_( \right )
		.string_( "Dry Level" );
	gui.slider.new( win, Rect( 0, 0, 240, 20 ))
		.value_( levelSpec.unmap( dryLevel.ampdb ))
		.action_({ arg b;
			dryLevel = levelSpec.map( b.value ).dbamp;
			lbDryLevel.string = levelSpec.map( b.value ).round( 0.1 ).asString ++ " dB";
			synth.set( \dryLevel, dryLevel );
		});
	lbDryLevel = gui.staticText.new( win, Rect( 0, 0, 48, 24 ))
		.string_( "0 dB" );
	flow.nextLine;

	gui.staticText.new( win, Rect( 0, 0, 80, 24 ))
		.align_( \right )
		.string_( "Early Ref. Lvl" );
	gui.slider.new( win, Rect( 0, 0, 240, 20 ))
		.value_( levelSpec.unmap( earlyLevel.ampdb ))
		.action_({ arg b;
			earlyLevel = levelSpec.map( b.value ).dbamp;
			lbEarlyLevel.string = levelSpec.map( b.value ).round( 0.1 ).asString ++ " dB";
			synth.set( \earlyLevel, earlyLevel );
		});
	lbEarlyLevel = gui.staticText.new( win, Rect( 0, 0, 48, 24 ))
		.string_( "-6 dB" );
	flow.nextLine;

	gui.staticText.new( win, Rect( 0, 0, 80, 24 ))
		.align_( \right )
		.string_( "Reverb Level" );
	gui.slider.new( win, Rect( 0, 0, 240, 20 ))
		.value_( levelSpec.unmap( tailLevel.ampdb ))
		.action_({ arg b;
			tailLevel = levelSpec.map( b.value ).dbamp;
			lbTailLevel.string = levelSpec.map( b.value ).round( 0.1 ).asString ++ " dB";
			synth.set( \tailLevel, tailLevel );
		});
	lbTailLevel = gui.staticText.new( win, Rect( 0, 0, 48, 24 ))
		.string_( "-12 dB" );
	flow.nextLine;

	flow.shift( 8, 8 );

	fStop = {
		synth.free;
		synth = nil;
	};
	
	fCreateDef = { arg numChannels;
		SynthDef( \filterGVerb, { arg bus = 0, roomSize = 30, revTime = 3, outDamping = 0.5, inDamping = 0.5, spread = 15, dryLevel = 0, earlyLevel = 0.5, tailLevel = 0.25;
			var inp, filter;
			inp 		= In.ar( bus, numChannels ).asArray;
			filter	= 0;
			numChannels.do({ arg ch;
				filter = filter + GVerb.ar( inp[ ch ], roomSize, revTime, outDamping, inDamping, spread, dryLevel, earlyLevel, tailLevel, maxRoomSize );
			});
			ReplaceOut.ar( bus, filter );
		});
	};
	
	ggBypass = gui.button.new( win, Rect( 0, 0, 80, 24 ))
		.states_([[ "Bypass" ], [ "Bypass", Color.black, Color.yellow ]])
		.value_( 1 )
		.action_({ arg b;
			if( b.value == 0, {
				{
					var msg, grpInput, diskBus, localThresh, localFFTSize, def;
					
						msg = e.query( '/doc/active/sc', [ \diskBusIndex, \diskBusNumChannels, \inputGroup ]);
						if( msg.notNil, {
						diskBus 	= Bus( \audio, msg[ 0 ], msg[ 1 ], e.scsynth );
						grpInput	= Group.basicNew( e.scsynth, msg[ 2 ]);
						def = fCreateDef.value( diskBus.numChannels );
						synth = Synth.basicNew( def.name, e.scsynth );
						def.send( e.scsynth, synth.newMsg( grpInput, [ \bus, diskBus.index, \roomSize, roomSize, \revTime, revTime, \outDamping, outDamping, \inDamping, inDamping, \spread, spread, \dryLevel, dryLevel, \earlyLevel, earlyLevel, \tailLevel, tailLevel ], \addAfter ));
					});
				}.fork( AppClock );
			}, {
				fStop.value;
			});
		});
	ggBypass.onClose = fStop; // XXX NEVER GETS CALLED

	ggRender = gui.button.new( win, Rect( 0, 0, 80, 24 ))
		.states_([[ "Render" ]])
		.action_({ arg b;
			{
				var msg, msg2, msg3, oscFileName, oscFile, fna, audioFileName, numChannels, rate, totalLen, cmd, prog, newProg,
				    localThresh, def, oscFileName2, oscRaw;
				msg		= e.sendMsgSync( '/main', \createTempFile );
				msg2	= e.sendMsgSync( '/main', \createTempFile );
				msg3	= e.sendMsgSync( '/main', \createTempFile );
				if( msg.notNil && msg2.notNil && msg3.notNil, {
					oscFileName = msg.first;
					oscFileName2 = msg2.first;
					audioFileName = msg3.first;
					msg = e.query( '/doc/active/sc', [ \diskBusNumChannels ]);
					if( msg.notNil, {
						numChannels = msg.first;
						msg = e.query( '/doc/active/timeline', [ \rate, \selectionStart, \selectionStop ]);
						if( msg.notNil, {
							rate = msg[ 0 ];
							totalLen = (msg[ 2 ] - msg[ 1 ]) / rate;
							(oscFileName.asString.asCompileString ++ " (numCh = "++numChannels++"; rate = "++rate++"; duration = "++totalLen++") --> " ++ audioFileName).postln;
							"Creating NRT file...".postln;
							e.sendMsg( '/doc/active/sc', \createNRTFile, oscFileName, 0, 0, numChannels, rate );
							
//							[ "oscFileName", oscFileName, "oscFileName2", oscFileName2 ].postcs;
							
// XXX BROKEN
//							e.sync;
e.query( '/main', [ \version ]);
							"...done".postln;
							
							1.0.wait;
							
//							"Reading NRT file...".postln;
//
//							oscFile = OSCFile.read( oscFileName );
//							"...done".postln;
//
							"Creating merged NRT file...".postln;
// oscFileName2="/tmp/test.osc";
						fna = EisKNRTFile.openWrite( oscFileName2.asString );
							"-----------3".postln;
						def = fCreateDef.value( numChannels );
						synth = Synth.basicNew( def.name, e.scsynth );
						fna.sendBundle( 0.0, def.recvMsg );
						fna.sendBundle( 0.0, synth.newMsg( Group.basicNew( e.scsynth, 0 ), [ \threshold, localThresh, \bus, 0 ], \addToTail ));
//							"-----------4".postln;
//("cp " ++ oscFileName ++ " /tmp/test2.osc").systemCmd;
//							"-----------4.5".postln;
//oscFileName = "/tmp/test2.osc";
						oscFile = File( oscFileName.asString, "rb" );
//							"-----------5".postln;
						oscRaw = Int8Array.newClear( oscFile.length );
						oscFile.read( oscRaw );
//							"-----------6".postln;
						oscFile.close;
						fna.file.write( oscRaw );
//							"-----------7".postln;
//						oscFile.packets.do({ arg p;
//							fna.sendRaw( p.asRawOSC );
//						});
						fna.closeFile;
						
							"...done".postln;
// audioFileName = desktopFolder ++ "test.aif";
							cmd = "./scsynth -i 0 -o "++numChannels++" -N " ++ oscFileName2 ++ " _ " ++ audioFileName ++ " "++rate++" AIFF float";
														
							ggProgress.knobColor_( Color.blue( 0.3 ));
							ggProgress.hi = 0.0;
							prog = 0.0;
							ggProgress.visible = true;
							Pipe.do( cmd, { arg line, i;
//line.postcs;
								if( line.beginsWith( "nextOSCPacket " ), {
									newProg = line.copyToEnd( 14 ).asFloat / totalLen;
									if( (newProg - prog) > 0.01, {
										prog = newProg;
										ggProgress.hi = newProg;
										0.yield;
									});
								}, {
									line.postln;
									0.yield;
								});
							});
							ggProgress.hi = 1.0;
							ggProgress.knobColor = Color.green( 0.75 );
							
//							e.sendMsg( '/doc/active', \editMode, \overwrite );
							e.sendMsg( '/doc/active', \replace, audioFileName );

						}, {
							"timeout".warn;	
						});
					}, {
						"timeout".warn;	
					});
				}, {
					"timeout".warn;	
				});
			}.fork( AppClock );
		});

	ggProgress = GUI.rangeSlider.new( win, Rect( 0, 0, 172, 24 ))
		.canFocus_( false )
		.visible_( false );
} { arg error;
	error.reportError;
};

};
)
