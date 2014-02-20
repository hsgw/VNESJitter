/**************************************************************************************
	VNESJitter is written by Takuya Urakawa(urkwtky@gmail.com)

		This is a mxj object for playng NES games on Max.
		These code are depending vNES (Copyright 2006-2010 Jamie Sanders)
		and referenced vNESp5(by Mar Canet)

		vNES   - http://www.openemulation.com/vnes/index.html
		vNESp5 - http://github.com/mcanet/vNESp5/

	This program is free software: you can redistribute it and/or modify it under
	the terms of the GNU General Public License as published by the Free Software
	Foundation, either version 3 of the License, or (at your option) any later
	version.

	This program is distributed in the hope that it will be useful, but WITHOUT ANY
	WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
	PARTICULAR PURPOSE.  See the GNU General Public License for more details.

	You should have received a copy of the GNU General Public License along with
	this program.  If not, see <http://www.gnu.org/licenses/>.


	HISTORY

		v 1.0 - first release


	TODO
		- direct memory access
		- adjust CPU speed
		- state save and load

**************************************************************************************/


import static vNESJitter.Constants.*;

import java.awt.image.BufferedImage;

import vNES.KbInputHandler;
import vNES.vNES;
import vNESJitter.Constants.Command;

import com.cycling74.jitter.JitterMatrix;
import com.cycling74.max.Atom;
import com.cycling74.max.DataTypes;
import com.cycling74.max.MaxObject;

public class VNESJitter extends MaxObject{

	private int counter = 0;

	// JitterMatrix matrix = new JitterMatrix(1, "char", WIDTH, HEIGHT);
	JitterMatrix matrix = new JitterMatrix(1, "char", WIDTH, HEIGHT-16); // actual display size W:256 H:224
	private String fileName;

	private vNES emu;


	// constructor
	VNESJitter(){

		declareInlets(new int[]{
				DataTypes.ANYTHING, // command
				DataTypes.LIST, 	// controller player 1
				DataTypes.LIST, 	// controller player 2
				DataTypes.ANYTHING, // frame update
				});

		setInletAssist(0, "(massage) command");
		setInletAssist(1, "(list) 1P Contorller state [A, B, START, SELECT, UP, DOWN, LEFT, RIGHT] 0-1");
		setInletAssist(2, "(list) 2P Contorller state [A, B, START, SELECT, UP, DOWN, LEFT, RIGHT] 0-1");
		setInletAssist(3, "(bang) update frame");

		declareOutlets(new int[]{
				DataTypes.ALL,  // jitter matrix out
				DataTypes.LIST	// data
				});

		setOutletAssist(0, "(matrix) out");
		setOutletAssist(1, "(list) data out");

		emu = new vNES();
		emu.init();
		emu.nes.enableSound(false);

	}


	/////////////////////////////////////////////////////////////////////////////////////////////////////
	// Max Object
	/////////////////////////////////////////////////////////////////////////////////////////////////////

	// controller update
	public void list(int[] intArray){
		if(getInlet() == 1){
			controller(0,intArray);
		}else if(getInlet() == 2){
			controller(1,intArray);
		}
	}

	// generate jitter matrix
	public void bang(){
		if(getInlet() == 3){
			if(emu.started){
				matrix = updatePixels();
			}
			outlet(0,"jit_matrix",matrix.getName());
		}
	}

	// message + arg(s)
	public void anything(String message, Atom[] atomArray){
		Command com;

		try {
			com = COMMAND_LIST.get(message);
		} catch (Exception e) {
			// e.printStackTrace();
			error("wrong message!");
			return;
		}

		switch(com){

			// load rom
			case ROM :
				loadRom(atomArray[0].toString());
				break;

			// set emulating speed
			case SET_SPEED :
				if(atomArray[0].isFloat() && atomArray[0].getFloat() >= 0.0){
					int newFramerate = Math.round(atomArray[0].getFloat() * DEFAULT_FRAMERATE);
					if(newFramerate > 0){
						if(!emu.nes.isRunning()) emu.nes.startEmulation();
						emu.nes.setFramerate(newFramerate);
					}
				}
				break;

			// enable sound output from OS default port (not in max/msp signal out)
			case SOUND:
				if(atomArray[0].isInt()){
					if(atomArray[0].getInt() == 1) emu.nes.enableSound(true);
					else if(atomArray[0].getInt() == 0) emu.nes.enableSound(false);
				}
				break;

			default: break;
		}
	}

	// reset game and reload rom
	public void reset(){
		if(fileName != null){
			loadRom(fileName);
		}
		emu.nes.startEmulation();
	}

	// stop game
	public void stop(){
		emu.nes.stopEmulation();
	}

	// restart game
	public void start(){
		emu.nes.startEmulation();
	}

	// destroy mxj object
	protected void notifyDeleted() {
		emu.destroy();
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////
	// 	private methods
	/////////////////////////////////////////////////////////////////////////////////////////////////////

	private void loadRom(String file){
		fileName = file;
		if(emu.started){
			emu.nes.loadRom(fileName);
		}else{
			emu.rom = fileName;
			emu.start();
		}
	}

	private JitterMatrix updatePixels(){

		BufferedImage internalImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_BGR);
		internalImage.getRaster().setDataElements(0, 0, WIDTH, HEIGHT, emu.nes.ppu.pixelEndRender);

		// crop TV size
		BufferedImage tvSizeImage = internalImage.getSubimage(8, 8, WIDTH-16, HEIGHT-16);

		return new JitterMatrix(tvSizeImage);


//		too slow!!
//		int pixels[] = emu.nes.ppu.pixelEndRender;//ABGR
//		for(int x = 0; x < matrix.getDim()[0]; x++){
//			for(int y = 0; y < matrix.getDim()[1]; y++){
//				int color[] = {
//									(pixels[x + y * matrix.getDim()[0]] >> 24) & 0xff, // alpha
//									(pixels[x + y * matrix.getDim()[0]] ) & 0xff,      // blue
//									(pixels[x + y * matrix.getDim()[0]] >> 8) & 0xff,  // green
//									(pixels[x + y * matrix.getDim()[0]] >> 16) & 0xff // red
//								};
//				matrix.setcell2d(x,y,color);
//			}
//		}
	}

	private void controller(int player, int[] keys){
		// keymap {A, B, START, SELECT, UP, DOWN, LEFT, RIGHT}

		KbInputHandler input;
		if(player == 0){
			input = (KbInputHandler)emu.nes.getGui().getJoy1();
		}else if(player == 1){
			input = (KbInputHandler)emu.nes.getGui().getJoy2();
		}else{
			error("Wrong Data : Player must be 0 or 1 " + player);
			return;
		}

		for(int i=0;i<8;i++){
			if(keys[i] == 0 || keys[i] == 1){
				// update control
				input.allKeysState[input.keyMapping[i]] = keys[i]!=0 ? true:false;
			}else{
				error("Wrong Data : Controller list must be 0 or 1 " + player + " " + keys + "key :" + i);
				return;
			}
		}
	}

}
