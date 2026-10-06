package com.sighs.apricityui.chunkmap;

import com.mojang.blaze3d.platform.GlStateManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

final class AuiMapGlProgram implements AutoCloseable {
    private final int program;
    private final Map<String, Integer> uniforms = new HashMap<>();

    AuiMapGlProgram(String name) {
        int vertex = 0, fragment = 0, linked = 0;
        try {
            vertex = compile(name, "vsh", GL20.GL_VERTEX_SHADER);
            fragment = compile(name, "fsh", GL20.GL_FRAGMENT_SHADER);
            linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vertex);
            GL20.glAttachShader(linked, fragment);
            String[] attributes = {"Position", "Color", "UV0", "UV2", "Normal"};
            for (int i = 0; i < attributes.length; i++) GL20.glBindAttribLocation(linked, i, attributes[i]);
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == 0)
                throw new IllegalStateException("Cannot link AUI map shader " + name + ": " + GL20.glGetProgramInfoLog(linked));
            program = linked;
        } catch (RuntimeException failure) {
            if (linked != 0) GL20.glDeleteProgram(linked);
            throw failure;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
    }

    private static int compile(String name, String suffix, int type) {
        var id = new ResourceLocation("apricityui", "shaders/core/" + name + "." + suffix);
        String source;
        try (var input = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open()) {
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load AUI map shader " + id, failure);
        }
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String message = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Cannot compile AUI map shader " + id + ": " + message);
        }
        return shader;
    }

    void bind() { GlStateManager._glUseProgram(program); }
    private int uniform(String name) { return uniforms.computeIfAbsent(name, key -> GL20.glGetUniformLocation(program, key)); }
    void matrix(String name, Matrix4f matrix) {
        try (var stack = MemoryStack.stackPush()) {
            GL20.glUniformMatrix4fv(uniform(name), false, matrix.get(stack.mallocFloat(16)));
        }
    }
    void vector(String name, float x, float y, float z, float w) { GL20.glUniform4f(uniform(name), x, y, z, w); }
    void sampler(String name, int unit) { GL20.glUniform1i(uniform(name), unit); }
    @Override public void close() { GL20.glDeleteProgram(program); }
}
