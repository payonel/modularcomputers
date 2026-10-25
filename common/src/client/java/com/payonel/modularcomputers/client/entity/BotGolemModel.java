package com.payonel.modularcomputers.client.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The bot golem's model: an iron golem's shape, a block high (15 pixels): a head whose front is its
 * screen, a broad body, long arms hanging to its knees, short legs. Its arms and legs swing as it
 * walks, as an iron golem's. Its texture's layout is scripts/art/bot_golem_textures.py's.
 */
public final class BotGolemModel extends EntityModel<BotGolemRenderer.State> {
	/** The texture's size, in texels. */
	public static final int TEXTURE_WIDTH = 64;
	public static final int TEXTURE_HEIGHT = 32;
	/** Where the head's front is (the screen's face), in model pixels (its -Z face). */
	public static final float HEAD_FRONT = -3;
	/** The head's top, in pixels above the ground. */
	public static final float HEAD_TOP = 15;
	/** How far a stride swings an arm or a leg, in radians, and its period in walk steps (an iron golem's). */
	private static final float SWING = 1.0f;
	private static final float STRIDE = 13;

	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public BotGolemModel(ModelPart root) {
		super(root);
		rightArm = root.getChild("right_arm");
		leftArm = root.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	/** Its parts, in model pixels (y down, the ground at 24), and where each takes its texture. */
	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -6, -3, 7, 6, 6), PartPose.offset(0, 15, 0));
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 12).addBox(-4, 0, -2.5f, 8, 6, 5), PartPose.offset(0, 15, 0));
		root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(28, 0).addBox(-2, -0.5f, -1.5f, 2, 8, 3), PartPose.offset(-4, 15.5f, 0));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 0).mirror().addBox(0, -0.5f, -1.5f, 2, 8, 3), PartPose.offset(4, 15.5f, 0));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(28, 12).addBox(-1.5f, 0, -1.5f, 3, 3, 3), PartPose.offset(-2, 21, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(42, 12).mirror().addBox(-1.5f, 0, -1.5f, 3, 3, 3), PartPose.offset(2, 21, 0));
		return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
	}

	/** Moves to its right hand's pivot, as its arm swings (where what it holds is drawn from). */
	public void translateToRightArm(com.mojang.blaze3d.vertex.PoseStack pose) {
		root().translateAndRotate(pose);
		rightArm.translateAndRotate(pose);
	}

	@Override
	public void setupAnim(BotGolemRenderer.State state) {
		super.setupAnim(state);
		float swing = Mth.triangleWave(state.walkAnimationPos, STRIDE) * state.walkAnimationSpeed * SWING;
		rightArm.xRot = swing;
		leftArm.xRot = -swing;
		rightLeg.xRot = -swing;
		leftLeg.xRot = swing;
	}
}
