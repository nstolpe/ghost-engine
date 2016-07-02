package com.hh.gdxtutorial.ai.states;

import aurelienribon.tweenengine.BaseTween;
import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenCallback;
import aurelienribon.tweenengine.equations.Linear;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.ai.msg.MessageManager;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.graphics.g3d.particles.emitters.RegularEmitter;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.hh.gdxtutorial.ai.Messages;
import com.hh.gdxtutorial.entity.components.EffectsComponent;
import com.hh.gdxtutorial.entity.components.Mappers;
import com.hh.gdxtutorial.entity.components.ModelInstanceComponent;
import com.hh.gdxtutorial.entity.components.TargetComponent;
import com.hh.gdxtutorial.helpers.Utility;
import com.hh.gdxtutorial.singletons.Manager;
import com.hh.gdxtutorial.tweenengine.accessors.QuaternionAccessor;
import com.hh.gdxtutorial.tweenengine.accessors.SlerpTween;
import com.hh.gdxtutorial.tweenengine.accessors.Vector3Accessor;

/**
 * Created by nils on 6/26/16.
 */
public enum MobState implements State<Entity> {
	REST() {
		@Override
		public void enter(Entity mob) {
			System.out.println("enter rest");
			Mappers.MODEL_INSTANCE.get(mob).controller.animate("skeleton|rest", -1, null, 1);
		}

		@Override
		public void update(Entity mob) {
		}
	},
	ACTIVE() {
		@Override
		public void enter(Entity mob) {
			Vector3 actorPosition = Mappers.POSITION.get(mob).position();
			Quaternion actorRotation = Mappers.ROTATION.get(mob).rotation();
			Vector3 targetPosition = new Vector3(MathUtils.random(-20, 20), 0, MathUtils.random(-20, 20));
		}
	},
	TARGETING() {
		@Override
		public void enter(Entity mob) {
			final StateMachine<Entity, MobState> stateMachine = Mappers.MOB.get(mob).stateMachine;
			TweenCallback callback = new TweenCallback() {
				@Override
				public void onEvent(int i, BaseTween<?> baseTween) {
					stateMachine.changeState(ATTACK_PRE);
				}
			};
			faceTarget(mob, Mappers.TARGET.get(mob).target, callback);
		}
	},
	ATTACK_PRE() {
		@Override
		public void enter(Entity mob) {
			final StateMachine<Entity, MobState> stateMachine = Mappers.MOB.get(mob).stateMachine;
			Mappers.MODEL_INSTANCE.get(mob).controller.setAnimation(
				"skeleton|attack.pre",
				new AnimationController.AnimationListener() {
					@Override
					public void onEnd(AnimationController.AnimationDesc animation) {
						stateMachine.changeState(ATTACK);
					}

					@Override
					public void onLoop(AnimationController.AnimationDesc animation) {
					}
				});
		}
	},
	ATTACK() {
		@Override
		public void enter(final Entity mob) {
			final StateMachine<Entity, MobState> stateMachine = Mappers.MOB.get(mob).stateMachine;
			final ModelInstanceComponent modelInstanceComponent = Mappers.MODEL_INSTANCE.get(mob);
			final EffectsComponent.Effect blast = Mappers.EFFECTS.get(mob).getEffect("blast");

			// set the 'blast' effect's position to the model's attach.projectile node
			Matrix4 attachmentMatrix = modelInstanceComponent.instance.transform.cpy().mul(modelInstanceComponent.instance.getNode("attach.projectile").globalTransform);
			blast.position = attachmentMatrix.getTranslation(blast.position);
			// turn on the blast emmitter
			blast.emitter.setEmissionMode(RegularEmitter.EmissionMode.Enabled);

			modelInstanceComponent.controller.setAnimation(
				"skeleton|attack",
				new AnimationController.AnimationListener() {
					@Override
					public void onEnd(AnimationController.AnimationDesc animation) {
						stateMachine.changeState(ATTACK_POST);
					}
					@Override
					public void onLoop(AnimationController.AnimationDesc animation) {}
				});
		}

		@Override
		public void update(Entity mob) {
			final ModelInstanceComponent modelInstanceComponent = Mappers.MODEL_INSTANCE.get(mob);
			final EffectsComponent.Effect blast = Mappers.EFFECTS.get(mob).getEffect("blast");

			Matrix4 attachmentMatrix = modelInstanceComponent.instance.transform.cpy().mul(modelInstanceComponent.instance.getNode("attach.projectile").globalTransform);
			blast.position = attachmentMatrix.getTranslation(blast.position);
		}
	},
	ATTACK_POST() {
		@Override
		public void enter(final Entity mob) {
			final StateMachine<Entity, MobState> stateMachine = Mappers.MOB.get(mob).stateMachine;
			final Vector3 position = Mappers.POSITION.get(mob).position;
			final Vector3 targetPosition = Mappers.POSITION.get(Mappers.TARGET.get(mob).target).position;
			final EffectsComponent.Effect blast = Mappers.EFFECTS.get(mob).getEffect("blast");

			Mappers.MODEL_INSTANCE.get(mob).controller.setAnimation(
				"skeleton|attack.post",
				new AnimationController.AnimationListener() {
					@Override
					public void onEnd(AnimationController.AnimationDesc animation) {
						mob.remove(TargetComponent.class);
						// @TODO move Tween out.
						Tween.to(blast.position,
							Vector3Accessor.XYZ,
							position.dst(targetPosition.x, blast.position.y, targetPosition.z) / 16)
							.target(targetPosition.x, blast.position.y, targetPosition.z)
							.ease(Linear.INOUT)
							.setCallback(new TweenCallback() {
								@Override
								public void onEvent(int i, BaseTween<?> baseTween) {
									stateMachine.changeState(REST);
									MessageManager.getInstance().dispatchMessage(0, Messages.ADVANCE_TURN_CONTROL);
									blast.emitter.setEmissionMode(RegularEmitter.EmissionMode.EnabledUntilCycleEnd);
								}
							})
							.start(Manager.getInstance().tweenManager());
//						stateMachine.changeState(REST);
//						mob.remove(TargetComponent.class);
					}

					@Override
					public void onLoop(AnimationController.AnimationDesc animation) {
					}
				});
		}
		@Override
		public void update(Entity mob) {
			// projectile has been launched if there's no target, so the Tween will take over.
			if (Mappers.TARGET.has(mob)) {
				final ModelInstanceComponent modelInstanceComponent = Mappers.MODEL_INSTANCE.get(mob);
				final EffectsComponent.Effect blast = Mappers.EFFECTS.get(mob).getEffect("blast");

				Matrix4 attachmentMatrix = modelInstanceComponent.instance.transform.cpy().mul(modelInstanceComponent.instance.getNode("attach.projectile").globalTransform);
				blast.position = attachmentMatrix.getTranslation(blast.position);
			}
		}
	},
	GLOBAL() {
	};

	@Override
	public void enter(Entity mob) {
	}

	@Override
	public void update(Entity mob) {
	}

	@Override
	public void exit(Entity mob) {
	}

	@Override
	public boolean onMessage(Entity mob, Telegram telegram) {
		return false;
	}

	/**
	 * Sets up data to face a target and executes a Tween
	 *
	 * @param actor
	 * @param target
	 * @TODO move this somewhere else. Library. Action Library?
	 */
	public void faceTarget(Entity actor, Entity target, TweenCallback callback) {
		Vector3 position = Mappers.POSITION.get(actor).position;
		Quaternion rotation = Mappers.ROTATION.get(actor).rotation;
		Vector3 targetPosition = Mappers.POSITION.get(target).position;

		Quaternion rotationTo = Utility.getRotationTo(position, targetPosition, rotation);
		Quaternion qd = rotation.cpy().conjugate().mul(rotationTo);

		float angle = 2 * (float) Math.atan2(new Vector3(qd.x, qd.y, qd.z).len(), qd.w);

		// @TODO make duration configurable instead of just angle / 4
		// move to Tween library. Tween.rotateTo(origin, target, duration, callback, easing)
		SlerpTween.to(rotation, QuaternionAccessor.ROTATION, angle / 4)
			.target(rotationTo.x, rotationTo.y, rotationTo.z, rotationTo.w)
			.ease(Linear.INOUT)
			.setCallback(callback).start(Manager.getInstance().tweenManager());
	}

	/**
	 * Ensures that an object is the right kind of Message data type.
	 *
	 * @param messageData
	 * @param messageClass
	 */
	protected void validateMessageDataOrExit(Object messageData, Class<?> messageClass) {
		if (!messageClass.isInstance(messageData)) Gdx.app.exit();
	}
}