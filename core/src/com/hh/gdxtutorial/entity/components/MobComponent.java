package com.hh.gdxtutorial.entity.components;

import aurelienribon.tweenengine.BaseTween;
import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenCallback;
import aurelienribon.tweenengine.equations.Linear;
import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.ai.msg.MessageManager;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.particles.emitters.RegularEmitter;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.hh.gdxtutorial.ai.Messages;
import com.hh.gdxtutorial.helpers.Utility;
import com.hh.gdxtutorial.singletons.Manager;
import com.hh.gdxtutorial.tweenengine.accessors.QuaternionAccessor;
import com.hh.gdxtutorial.tweenengine.accessors.SlerpTween;
import com.hh.gdxtutorial.tweenengine.accessors.Vector3Accessor;

/**
 * Created by nils on 6/5/16.
 */
public class MobComponent implements Component {
	public StateMachine<MobComponent, MobState> stateMachine;

	public MobComponent() {
		stateMachine = new DefaultStateMachine<MobComponent, MobState>(this, MobState.REST, MobState.GLOBAL);
	}

	public enum MobState implements State<MobComponent> {
		REST() {
			@Override
			public boolean onMessage(MobComponent ai, Telegram telegram) {
				Gdx.app.log("onMessage", "REST");

				if (telegram.message == Messages.REST) {
					validateMessageDataOrExit(telegram.extraInfo, Messages.TargetMessageData.class);
					final Messages.TargetMessageData messageData = (Messages.TargetMessageData) telegram.extraInfo;
					final StateMachine<MobComponent, MobState> stateMachine = ai.stateMachine;
					final ModelInstanceComponent mic = Mappers.MODEL_INSTANCE.get(messageData.actor);
					mic.controller.animate("skeleton|rest", 1, 1.0f, new AnimationController.AnimationListener() {
						@Override
						public void onEnd(AnimationController.AnimationDesc animation) {
						}

						@Override
						public void onLoop(AnimationController.AnimationDesc animation) {

						}
					}, 1.0f);
				}
				return false;
			}
		},
		TARGETING() {
			/**
			 * Initiate the rotate to target SlerpTween.
			 * ATTACK_PRE is initiated in by the callback.
			 * @param ai
			 * @param telegram
			 * @return
			 */
			@Override
			public boolean onMessage(MobComponent ai, Telegram telegram) {
				if (telegram.message == Messages.TARGET_ACQUIRED) {
					Gdx.app.log("onMessage", "TARGETING");
					validateMessageDataOrExit(telegram.extraInfo, Messages.TargetMessageData.class);
					final Messages.TargetMessageData messageData = (Messages.TargetMessageData) telegram.extraInfo;

					final StateMachine<MobComponent, MobState> stateMachine = ai.stateMachine;
					TweenCallback callback = new TweenCallback() {
						@Override
						public void onEvent(int i, BaseTween<?> baseTween) {
							stateMachine.changeState(ATTACK_PRE);
							MessageManager.getInstance().dispatchMessage(stateMachine, stateMachine, Messages.ATTACK_PRE, messageData);
						}
					};
					faceTarget(messageData.actor, messageData.target, callback);
					return true;
				}
				return false;
			}
		},
		ATTACK_PRE() {
			/**
			 * Initiates the attack.pre animation and ATTACK_PRE state.
			 * @param ai
			 * @param telegram
			 * @return
			 */
			@Override
			public boolean onMessage(MobComponent ai, Telegram telegram) {
				if (telegram.message == Messages.ATTACK_PRE) {
					Gdx.app.log("onMessage", "ATTACK_PRE");

					validateMessageDataOrExit(telegram.extraInfo, Messages.TargetMessageData.class);
					final Messages.TargetMessageData messageData = (Messages.TargetMessageData) telegram.extraInfo;
					final StateMachine<MobComponent, MobState> stateMachine = ai.stateMachine;
					final ModelInstanceComponent mic = Mappers.MODEL_INSTANCE.get(messageData.actor);

					mic.controller.animate("skeleton|attack.pre", 1, 1.0f, new AnimationController.AnimationListener() {
						@Override
						public void onEnd(AnimationController.AnimationDesc animation) {
							stateMachine.changeState(ATTACK);
							MessageManager.getInstance().dispatchMessage(stateMachine, stateMachine, Messages.ATTACK, messageData);
						}

						@Override
						public void onLoop(AnimationController.AnimationDesc animation) {

						}
					}, 1.0f);

					return true;
				}
				return false;
			}
		},
		ATTACK() {
			/**
			 * Initiates the attack animation and enters the ATTACK state.
			 * @param ai
			 * @param telegram
			 * @return
			 * @TODO this is still too unwieldy, refactor it and abstact some of it.
			 */
			@Override
			public boolean onMessage(MobComponent ai, Telegram telegram) {
				if (telegram.message == Messages.ATTACK) {
					Gdx.app.log("onMessage", "ATTACK");

					validateMessageDataOrExit(telegram.extraInfo, Messages.TargetMessageData.class);
					final Messages.TargetMessageData messageData = (Messages.TargetMessageData) telegram.extraInfo;
					final StateMachine<MobComponent, MobState> stateMachine = ai.stateMachine;
					final ModelInstanceComponent mic = Mappers.MODEL_INSTANCE.get(messageData.actor);

					ModelInstance inst = Mappers.MODEL_INSTANCE.get(messageData.actor).instance();

					EffectsComponent.Effect blast = Mappers.EFFECTS.get(messageData.actor).getEffect("blast");
					Matrix4 transform = inst.transform.cpy().mul(inst.getNode("attach.projectile").globalTransform);

					blast.position = transform.getTranslation(blast.position);
					blast.emitter.setEmissionMode(RegularEmitter.EmissionMode.Enabled);
					final Vector3 p = blast.position;

					final Vector3 position = Mappers.POSITION.get(messageData.actor).position;
					final Vector3 targetPosition = Mappers.POSITION.get(messageData.target).position;

					mic.controller.animate("skeleton|attack", 1, 1.0f, new AnimationController.AnimationListener() {
						@Override
						public void onEnd(AnimationController.AnimationDesc animation) {
							stateMachine.changeState(ATTACK_POST);
							MessageManager.getInstance().dispatchMessage(stateMachine, stateMachine, Messages.ATTACK_POST, messageData);
							Tween.to(p, Vector3Accessor.XYZ, position.dst(targetPosition.x, p.y, targetPosition.z) / 16)
								.target(targetPosition.x, p.y, targetPosition.z)
								.ease(Linear.INOUT)
								.setCallback(new TweenCallback() {
									@Override
									public void onEvent(int i, BaseTween<?> baseTween) {
										stateMachine.changeState(REST);
										MessageManager.getInstance().dispatchMessage(0, Messages.ADVANCE_TURN_CONTROL);
										Mappers.EFFECTS.get(messageData.actor).getEffect("blast").emitter.setEmissionMode(RegularEmitter.EmissionMode.EnabledUntilCycleEnd);

									}
								})
								.start(Manager.getInstance().tweenManager());
						}

						@Override
						public void onLoop(AnimationController.AnimationDesc animation) {}
					}, 1.0f);

					return true;
				}
				return false;
			}
		},
		ATTACK_POST() {
			/**
			 * Initiates the attack.post animation and ATTACK_POST state.
			 * @param ai
			 * @param telegram
			 * @return
			 */
			@Override
			public boolean onMessage(MobComponent ai, Telegram telegram) {
				if (telegram.message == Messages.ATTACK_POST) {
					Gdx.app.log("onMessage", "ATTACK_POST");

					validateMessageDataOrExit(telegram.extraInfo, Messages.TargetMessageData.class);
					final Messages.TargetMessageData messageData = (Messages.TargetMessageData) telegram.extraInfo;
					final StateMachine<MobComponent, MobState> stateMachine = ai.stateMachine;
					final ModelInstanceComponent mic = Mappers.MODEL_INSTANCE.get(messageData.actor);

					mic.controller.animate("skeleton|attack.post", 1, 1.0f, new AnimationController.AnimationListener() {
						@Override
						public void onEnd(AnimationController.AnimationDesc animation) {
							MessageManager.getInstance().dispatchMessage(stateMachine, stateMachine, Messages.REST, messageData);
						}

						@Override
						public void onLoop(AnimationController.AnimationDesc animation) {}
					}, 1.0f);
					return true;
				}
				return false;
			}
		},
		GLOBAL() {
			/**
			 * Global message handler. Activates states when their message type is received
			 * and passes the telegram to their handlers.
			 * @param ai
			 * @param telegram
			 * @return
			 */
			@Override
			public boolean onMessage(MobComponent ai, Telegram telegram) {
				switch (telegram.message) {
					case Messages.TARGET_ACQUIRED:
						ai.stateMachine.changeState(TARGETING);
						TARGETING.onMessage(ai, telegram);
						break;
					case Messages.ATTACK_PRE:
						ai.stateMachine.changeState(ATTACK_PRE);
						ATTACK_PRE.onMessage(ai, telegram);
						break;
					case Messages.ATTACK:
						ai.stateMachine.changeState(ATTACK);
						ATTACK.onMessage(ai, telegram);
						break;
					case Messages.ATTACK_POST:
						ai.stateMachine.changeState(ATTACK_POST);
						ATTACK_POST.onMessage(ai, telegram);
						break;
					case Messages.REST:
						ai.stateMachine.changeState(REST);
						REST.onMessage(ai, telegram);
						break;
					default:
						break;
				}
				return false;
			}
		};

		/**
		 * Ensures that an object is the right kind of Message data type.
		 * @param messageData
		 * @param messageClass
		 */
		protected void validateMessageDataOrExit(Object messageData, Class<?> messageClass) {
			if (!messageClass.isInstance(messageData)) Gdx.app.exit();
		}

		@Override
		public void enter(MobComponent ai) {}

		@Override
		public void update(MobComponent ai) {}

		@Override
		public void exit(MobComponent ai) {}

		@Override
		public boolean onMessage(MobComponent ai, Telegram telegram) { return false; }

		/**
		 * Sets up data to face a target and executes a Tween
		 * @TODO move this somewhere else. Library. Action Library?
		 * @param actor
		 * @param target
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
	}
}