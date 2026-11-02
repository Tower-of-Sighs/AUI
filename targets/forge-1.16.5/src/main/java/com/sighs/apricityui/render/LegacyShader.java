package com.sighs.apricityui.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class LegacyShader implements AutoCloseable {
    private final int program;
    private final Map<String, Integer> uniforms = new HashMap<>();

    public LegacyShader(IResourceManager resources, String name) throws IOException {
        JsonObject json = new JsonParser().parse(read(resources, name + ".json")).getAsJsonObject();
        String vertexName = json.get("vertex").getAsString().replace("apricityui:", "");
        String fragmentName = json.get("fragment").getAsString().replace("apricityui:", "");
        String vertex = vertexSource(read(resources, vertexName + ".vsh"));
        String fragment = fragmentSource(read(resources, fragmentName + ".fsh"));
        int vs = compile(GL20.GL_VERTEX_SHADER, vertex);
        int fs;
        try { fs = compile(GL20.GL_FRAGMENT_SHADER, fragment); }
        catch (IOException failure) { GL20.glDeleteShader(vs); throw failure; }
        program = GL20.glCreateProgram();
        try {
            GL20.glAttachShader(program, vs);
            GL20.glAttachShader(program, fs);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
                throw new IOException(name + ": " + GL20.glGetProgramInfoLog(program));
        } catch (IOException failure) {
            GL20.glDeleteProgram(program);
            throw failure;
        } finally {
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
        }
        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(program);
        try {
            if (json.has("uniforms")) for (var element : json.getAsJsonArray("uniforms")) {
                JsonObject uniform = element.getAsJsonObject();
                String key = uniform.get("name").getAsString();
                var values = uniform.getAsJsonArray("values");
                int count = uniform.get("count").getAsInt();
                float[] data = new float[count];
                for (int i = 0; i < count; i++) data[i] = values.get(values.size() == 1 ? 0 : i).getAsFloat();
                if (count == 16) {
                    FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
                    buffer.put(data).flip();
                    GL20.glUniformMatrix4fv(location(key), false, buffer);
                } else if ("int".equals(uniform.get("type").getAsString())) setInt(key, (int) data[0]);
                else set(key, data);
            }
            for (int i = 0; i < 12; i++) setInt("Sampler" + i, i);
        } finally { GL20.glUseProgram(previous); }
    }

    private static String read(IResourceManager resources, String file) throws IOException {
        try (var resource = resources.getResource(new ResourceLocation("apricityui", "shaders/core/" + file));
             InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static String vertexSource(String source) {
        return source.replace("#version 150", "#version 120")
                .replace("in vec3 Position;", "#define Position gl_Vertex.xyz")
                .replace("in vec2 UV0;", "#define UV0 gl_MultiTexCoord0.xy")
                .replace("out vec2", "varying vec2");
    }

    public static String fragmentSource(String source) {
        source = source.replace("#version 150", "#version 120")
                .replace("in vec2", "varying vec2")
                .replace("out vec4 fragColor;", "#define fragColor gl_FragColor")
                .replace("texture(", "texture2D(")
                .replace("index % 4", "int(mod(float(index), 4.0))");
        var arrays = java.util.regex.Pattern.compile("vec4 (\\w+)\\[(\\d+)\\] = vec4\\[\\d+\\]\\(([^;]+)\\);").matcher(source);
        StringBuffer result = new StringBuffer();
        while (arrays.find()) {
            String name = arrays.group(1);
            StringBuilder replacement = new StringBuilder("vec4 " + name + "[" + arrays.group(2) + "];");
            String[] values = arrays.group(3).split(",");
            for (int i = 0; i < values.length; i++) replacement.append(name).append('[').append(i).append("] = ").append(values[i].trim()).append(';');
            arrays.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement.toString()));
        }
        arrays.appendTail(result);
        return result.toString();
    }

    private static int compile(int type, String source) throws IOException {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IOException(log);
        }
        return shader;
    }

    private int location(String name) { return uniforms.computeIfAbsent(name, key -> GL20.glGetUniformLocation(program, key)); }
    public void bind() { GL20.glUseProgram(program); }
    public void setInt(String name, int value) { GL20.glUniform1i(location(name), value); }
    public void set(String name, float... values) {
        int location = location(name);
        switch (values.length) {
            case 1 -> GL20.glUniform1f(location, values[0]);
            case 2 -> GL20.glUniform2f(location, values[0], values[1]);
            case 3 -> GL20.glUniform3f(location, values[0], values[1], values[2]);
            case 4 -> GL20.glUniform4f(location, values[0], values[1], values[2], values[3]);
            default -> throw new IllegalArgumentException("Invalid uniform size: " + values.length);
        }
    }
    public void matrices() {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX, buffer);
        GL20.glUniformMatrix4fv(location("ProjMat"), false, buffer);
        buffer.clear();
        GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, buffer);
        GL20.glUniformMatrix4fv(location("ModelViewMat"), false, buffer);
    }
    @Override public void close() {
        if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == program) GL20.glUseProgram(0);
        GL20.glDeleteProgram(program);
    }
}
