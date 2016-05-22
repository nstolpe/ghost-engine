#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP
#endif

uniform sampler2D u_texture;
varying vec2 v_texCoords;
const float bluramount  = 1.0;
const float center      = 1.0;
const float stepSize    = 0.004;
const float steps       = 6.0;
const float minOffs     = (float(steps-1.0)) / -2.0;
const float maxOffs     = (float(steps-1.0)) / +2.0;
void main() {
	float amount;
	vec4 blurred;
	//Work out how much to blur based on the mid point
	amount = pow((v_texCoords.y * center) * 2.0 - 1.0, 2.0) * bluramount;
	//This is the accumulation of color from the surrounding pixels in the texture
	blurred = vec4(0.0, 0.0, 0.0, 1.0);
	//From minimum offset to maximum offset
	for (float offsX = minOffs; offsX <= maxOffs; ++offsX) {
		for (float offsY = minOffs; offsY <= maxOffs; ++offsY) {
			//copy the coord so we can mess with it
			vec2 temp_v_texCoords = v_texCoords.xy;
			//work out which uv we want to sample now
			temp_v_texCoords.x += offsX * amount * stepSize;
			temp_v_texCoords.y += offsY * amount * stepSize;
			//accumulate the sample
			blurred += texture2D(u_texture, temp_v_texCoords);
		}
	}
	//because we are doing an average, we divide by the amount (x AND y, hence steps * steps)\n
	blurred /= float(steps * steps);
	//return the final blurred color
	gl_FragColor = blurred;
}