package com.payonel.modularcomputers.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The case view's slot positions (in code) and its board texture (from
 * scripts/art/case_gui_textures.py) are made separately; this keeps them lined up.
 */
class CaseArtTest {
	private static final int SOCKET_INSIDE = 0x142218;
	private static final int SOCKET_RIM = 0x0C140E;

	private static BufferedImage board() throws IOException {
		try (InputStream in = CaseArtTest.class.getResourceAsStream("/assets/modularcomputers/textures/gui/case_board.png")) {
			assertThat(in).as("case_board.png on the classpath").isNotNull();
			return ImageIO.read(in);
		}
	}

	@Test
	void everySlotSitsInASocketOnTheBoard() throws IOException {
		BufferedImage board = board();
		for (int slot = 0; slot < CaseMenu.PART_SLOTS; slot++) {
			int x = CaseMenu.SLOT_POSITIONS[slot][0] - CaseMenu.BOARD_X;
			int y = CaseMenu.SLOT_POSITIONS[slot][1] - CaseMenu.BOARD_Y;
			for (int[] inside : new int[][] {{x, y}, {x + 15, y + 15}, {x + 8, y + 8}}) {
				assertThat(board.getRGB(inside[0], inside[1]) & 0xFFFFFF).as("slot %d inside at %s", slot, java.util.Arrays.toString(inside))
					.isEqualTo(SOCKET_INSIDE);
			}
			assertThat(board.getRGB(x - 1, y - 1) & 0xFFFFFF).as("slot %d rim", slot).isEqualTo(SOCKET_RIM);
		}
	}

	@Test
	void slotPositionsCoverEveryPartSlot() {
		assertThat(CaseMenu.SLOT_POSITIONS.length).isEqualTo(CaseMenu.PART_SLOTS);
		assertThat(CaseMenu.RAM_SLOTS + CaseMenu.LANES).isEqualTo(CaseMenu.SATA_SLOTS);
		assertThat(CaseMenu.SATA_SLOTS + CaseMenu.LANES).isEqualTo(CaseMenu.EXP_SLOTS);
		assertThat(CaseMenu.EXP_SLOTS + CaseMenu.LANES).isEqualTo(CaseMenu.PART_SLOTS);
	}
}
