package com.kyant.backdrop.internal

import org.intellij.lang.annotations.Language

/**
 * Sticky (metaball) liquid glass.
 *
 * Up to 3 rounded rects are merged with a polynomial smooth-min, so when they come close
 * a viscous "neck" forms between them, stretches, and finally snaps - exactly like liquid.
 * The merged shape is refracted (with radial chromatic dispersion), tinted and rim-lit in one pass.
 */
@Language("AGSL")
internal const val StickyLensShaderString = """
uniform shader content;

uniform float2 offset;
uniform float4 shape0;
uniform float4 shape1;
uniform float4 shape2;
uniform float3 radii;
uniform float shapeCount;
uniform float smoothness;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float chromaticAberration;
uniform float2 lightDir;
uniform float4 tint;
uniform float rimWidth;
uniform float rimAlpha;

float sdRoundedBox(float2 p, float4 box, float radius) {
    float2 q = abs(p - box.xy) - (box.zw - float2(radius));
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float smoothUnion(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}

float sceneField(float2 p) {
    float d = sdRoundedBox(p, shape0, radii.x);
    if (shapeCount > 1.5) {
        d = smoothUnion(d, sdRoundedBox(p, shape1, radii.y), smoothness);
    }
    if (shapeCount > 2.5) {
        d = smoothUnion(d, sdRoundedBox(p, shape2, radii.z), smoothness);
    }
    return d;
}

half4 main(float2 coord) {
    float2 p = coord + offset;
    float sd = sceneField(p);
    if (sd > 1.0) {
        return half4(0.0);
    }
    float alpha = clamp(0.5 - sd, 0.0, 1.0);

    float2 gradient = float2(
        sceneField(p + float2(1.0, 0.0)) - sceneField(p - float2(1.0, 0.0)),
        sceneField(p + float2(0.0, 1.0)) - sceneField(p - float2(0.0, 1.0))
    );
    float2 normal = gradient / max(length(gradient), 0.0001);

    float depth = max(-sd, 0.0);
    half4 color;
    if (depth >= refractionHeight) {
        color = content.eval(coord);
    } else {
        float x = 1.0 - depth / refractionHeight;
        float bend = (1.0 - sqrt(max(1.0 - x * x, 0.0))) * refractionAmount;
        float2 refractedCoord = coord - bend * normal;
        float2 dispersedCoord = -bend * normal * chromaticAberration;

        half4 c = half4(0.0);

        half4 red = content.eval(refractedCoord + dispersedCoord);
        c.r += red.r / 3.5;
        c.a += red.a / 7.0;

        half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
        c.r += orange.r / 3.5;
        c.g += orange.g / 7.0;
        c.a += orange.a / 7.0;

        half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
        c.r += yellow.r / 3.5;
        c.g += yellow.g / 3.5;
        c.a += yellow.a / 7.0;

        half4 green = content.eval(refractedCoord);
        c.g += green.g / 3.5;
        c.a += green.a / 7.0;

        half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
        c.g += cyan.g / 3.5;
        c.b += cyan.b / 3.0;
        c.a += cyan.a / 7.0;

        half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
        c.b += blue.b / 3.0;
        c.a += blue.a / 7.0;

        half4 purple = content.eval(refractedCoord - dispersedCoord);
        c.r += purple.r / 7.0;
        c.b += purple.b / 3.0;
        c.a += purple.a / 7.0;

        color = c;
    }

    color.rgb = mix(color.rgb, half3(tint.rgb), half(tint.a));

    float edge = 1.0 - smoothstep(0.0, rimWidth, depth);
    float facing = abs(dot(normal, lightDir));
    float rim = edge * (0.25 + 0.75 * pow(facing, 1.5)) * rimAlpha;
    color.rgb = clamp(color.rgb + half3(rim), half3(0.0), half3(1.0));

    return color * half(alpha);
}"""
