#include <android/log.h>
#include <android/input.h>
#include <android/native_window.h>
#include <android_native_app_glue.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>

#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <cstdint>

#define LOG_TAG "RacingNative3D"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifndef EGL_OPENGL_ES3_BIT_KHR
#define EGL_OPENGL_ES3_BIT_KHR 0x00000040
#endif

namespace {

constexpr float kPi = 3.14159265358979323846f;

struct Vec3 {
    float x = 0.0f;
    float y = 0.0f;
    float z = 0.0f;
};

Vec3 operator+(Vec3 a, Vec3 b) { return {a.x + b.x, a.y + b.y, a.z + b.z}; }
Vec3 operator-(Vec3 a, Vec3 b) { return {a.x - b.x, a.y - b.y, a.z - b.z}; }
Vec3 operator*(Vec3 a, float s) { return {a.x * s, a.y * s, a.z * s}; }

float dot(Vec3 a, Vec3 b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
Vec3 cross(Vec3 a, Vec3 b) {
    return {
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    };
}
float length(Vec3 v) { return std::sqrt(dot(v, v)); }
Vec3 normalize(Vec3 v) {
    const float len = length(v);
    if (len < 1.0e-6f) return {0.0f, 0.0f, 0.0f};
    return v * (1.0f / len);
}

struct Mat4 {
    float m[16]{};
};

Mat4 identity() {
    Mat4 r{};
    r.m[0] = r.m[5] = r.m[10] = r.m[15] = 1.0f;
    return r;
}

Mat4 multiply(const Mat4& a, const Mat4& b) {
    Mat4 r{};
    for (int col = 0; col < 4; ++col) {
        for (int row = 0; row < 4; ++row) {
            float sum = 0.0f;
            for (int k = 0; k < 4; ++k) {
                sum += a.m[k * 4 + row] * b.m[col * 4 + k];
            }
            r.m[col * 4 + row] = sum;
        }
    }
    return r;
}

Mat4 translation(Vec3 v) {
    Mat4 r = identity();
    r.m[12] = v.x;
    r.m[13] = v.y;
    r.m[14] = v.z;
    return r;
}

Mat4 scale(Vec3 v) {
    Mat4 r{};
    r.m[0] = v.x;
    r.m[5] = v.y;
    r.m[10] = v.z;
    r.m[15] = 1.0f;
    return r;
}

Mat4 rotationY(float angle) {
    const float c = std::cos(angle);
    const float s = std::sin(angle);
    Mat4 r = identity();
    r.m[0] = c;
    r.m[2] = -s;
    r.m[8] = s;
    r.m[10] = c;
    return r;
}

Mat4 perspective(float fovYRadians, float aspect, float nearZ, float farZ) {
    const float f = 1.0f / std::tan(fovYRadians * 0.5f);
    Mat4 r{};
    r.m[0] = f / aspect;
    r.m[5] = f;
    r.m[10] = (farZ + nearZ) / (nearZ - farZ);
    r.m[11] = -1.0f;
    r.m[14] = (2.0f * farZ * nearZ) / (nearZ - farZ);
    return r;
}

Mat4 lookAt(Vec3 eye, Vec3 center, Vec3 up) {
    const Vec3 f = normalize(center - eye);
    const Vec3 s = normalize(cross(f, up));
    const Vec3 u = cross(s, f);

    Mat4 r = identity();
    r.m[0] = s.x;
    r.m[4] = s.y;
    r.m[8] = s.z;
    r.m[1] = u.x;
    r.m[5] = u.y;
    r.m[9] = u.z;
    r.m[2] = -f.x;
    r.m[6] = -f.y;
    r.m[10] = -f.z;
    r.m[12] = -dot(s, eye);
    r.m[13] = -dot(u, eye);
    r.m[14] = dot(f, eye);
    return r;
}

GLuint compileShader(GLenum type, const char* source) {
    const GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);

    GLint ok = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (ok != GL_TRUE) {
        char log[1024]{};
        GLsizei len = 0;
        glGetShaderInfoLog(shader, sizeof(log), &len, log);
        LOGE("Shader compile failed: %s", log);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

GLuint createProgram() {
    static constexpr const char* kVs = R"GLSL(
        #version 300 es
        precision highp float;
        layout(location = 0) in vec3 aPosition;
        uniform mat4 uMvp;
        void main() {
            gl_Position = uMvp * vec4(aPosition, 1.0);
        }
    )GLSL";

    static constexpr const char* kFs = R"GLSL(
        #version 300 es
        precision mediump float;
        uniform vec3 uColor;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(uColor, 1.0);
        }
    )GLSL";

    const GLuint vs = compileShader(GL_VERTEX_SHADER, kVs);
    const GLuint fs = compileShader(GL_FRAGMENT_SHADER, kFs);
    if (!vs || !fs) return 0;

    const GLuint program = glCreateProgram();
    glAttachShader(program, vs);
    glAttachShader(program, fs);
    glLinkProgram(program);
    glDeleteShader(vs);
    glDeleteShader(fs);

    GLint ok = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &ok);
    if (ok != GL_TRUE) {
        char log[1024]{};
        GLsizei len = 0;
        glGetProgramInfoLog(program, sizeof(log), &len, log);
        LOGE("Program link failed: %s", log);
        glDeleteProgram(program);
        return 0;
    }
    return program;
}

static constexpr float kCubeVertices[] = {
    -0.5f,-0.5f, 0.5f,  0.5f,-0.5f, 0.5f,  0.5f, 0.5f, 0.5f,
    -0.5f,-0.5f, 0.5f,  0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
     0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f, 0.5f,-0.5f,
     0.5f,-0.5f,-0.5f, -0.5f, 0.5f,-0.5f,  0.5f, 0.5f,-0.5f,
    -0.5f,-0.5f,-0.5f, -0.5f,-0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
    -0.5f,-0.5f,-0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f,-0.5f,
     0.5f,-0.5f, 0.5f,  0.5f,-0.5f,-0.5f,  0.5f, 0.5f,-0.5f,
     0.5f,-0.5f, 0.5f,  0.5f, 0.5f,-0.5f,  0.5f, 0.5f, 0.5f,
    -0.5f, 0.5f, 0.5f,  0.5f, 0.5f, 0.5f,  0.5f, 0.5f,-0.5f,
    -0.5f, 0.5f, 0.5f,  0.5f, 0.5f,-0.5f, -0.5f, 0.5f,-0.5f,
    -0.5f,-0.5f,-0.5f,  0.5f,-0.5f,-0.5f,  0.5f,-0.5f, 0.5f,
    -0.5f,-0.5f,-0.5f,  0.5f,-0.5f, 0.5f, -0.5f,-0.5f, 0.5f
};

struct Renderer {
    GLuint program = 0;
    GLuint vao = 0;
    GLuint vbo = 0;
    GLint mvpLocation = -1;
    GLint colorLocation = -1;

    bool init() {
        program = createProgram();
        if (!program) return false;

        glGenVertexArrays(1, &vao);
        glBindVertexArray(vao);
        glGenBuffers(1, &vbo);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, sizeof(kCubeVertices), kCubeVertices, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 3 * sizeof(float), nullptr);
        glBindVertexArray(0);

        mvpLocation = glGetUniformLocation(program, "uMvp");
        colorLocation = glGetUniformLocation(program, "uColor");

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        return true;
    }

    void destroy() {
        if (vbo) glDeleteBuffers(1, &vbo);
        if (vao) glDeleteVertexArrays(1, &vao);
        if (program) glDeleteProgram(program);
        vbo = vao = program = 0;
    }

    void cube(const Mat4& viewProjection, Vec3 pos, Vec3 size, float yaw, Vec3 color) const {
        Mat4 model = multiply(translation(pos), multiply(rotationY(-yaw), scale(size)));
        Mat4 mvp = multiply(viewProjection, model);
        glUniformMatrix4fv(mvpLocation, 1, GL_FALSE, mvp.m);
        glUniform3f(colorLocation, color.x, color.y, color.z);
        glDrawArrays(GL_TRIANGLES, 0, 36);
    }
};

struct Vehicle {
    Vec3 position{0.0f, 0.62f, 7.0f};
    float yaw = 0.0f;
    float speed = 0.0f;
    float steeringVisual = 0.0f;

    void update(float dt, float steer, float throttle, float brake) {
        constexpr float maxForward = 44.0f;
        constexpr float maxReverse = -8.0f;
        constexpr float engineAccel = 14.0f;
        constexpr float brakeAccel = 27.0f;
        constexpr float reverseAccel = 8.0f;

        if (throttle > 0.01f) {
            if (speed >= -0.5f) speed += engineAccel * throttle * dt;
            else speed += brakeAccel * throttle * dt;
        }

        if (brake > 0.01f) {
            if (speed > 0.8f) speed -= brakeAccel * brake * dt;
            else speed -= reverseAccel * brake * dt;
        }

        const float rolling = 1.2f + std::abs(speed) * 0.035f;
        if (std::abs(speed) > 0.01f) {
            const float sign = speed > 0.0f ? 1.0f : -1.0f;
            speed -= sign * rolling * dt;
            if ((sign > 0.0f && speed < 0.0f) || (sign < 0.0f && speed > 0.0f)) speed = 0.0f;
        }

        speed = std::clamp(speed, maxReverse, maxForward);

        const float speedFactor = std::clamp(std::abs(speed) / 9.0f, 0.0f, 1.0f);
        const float reverseSign = speed >= 0.0f ? 1.0f : -1.0f;
        yaw += steer * reverseSign * (0.35f + 0.72f * speedFactor) * dt;
        steeringVisual += (steer - steeringVisual) * std::min(1.0f, dt * 9.0f);

        const Vec3 forward{std::sin(yaw), 0.0f, -std::cos(yaw)};
        position = position + forward * (speed * dt);

        if (std::abs(position.x) > 6.8f) {
            speed *= std::pow(0.965f, dt * 60.0f);
        }
        position.x = std::clamp(position.x, -9.0f, 9.0f);

        if (position.z < -185.0f) position.z = 15.0f;
        if (position.z > 20.0f) position.z = -180.0f;
    }

    Vec3 forward() const {
        return {std::sin(yaw), 0.0f, -std::cos(yaw)};
    }
};

struct Engine {
    android_app* app = nullptr;
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLSurface surface = EGL_NO_SURFACE;
    EGLContext context = EGL_NO_CONTEXT;
    int width = 0;
    int height = 0;
    bool ready = false;

    Renderer renderer{};
    Vehicle vehicle{};

    float touchSteer = 0.0f;
    float touchThrottle = 0.0f;
    float touchBrake = 0.0f;
    float keySteer = 0.0f;
    float keyThrottle = 0.0f;
    float keyBrake = 0.0f;

    bool initDisplay() {
        if (!app || !app->window) return false;

        display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (display == EGL_NO_DISPLAY || !eglInitialize(display, nullptr, nullptr)) {
            LOGE("eglInitialize failed");
            return false;
        }

        const EGLint configAttribs[] = {
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 24,
            EGL_NONE
        };

        EGLConfig config = nullptr;
        EGLint numConfigs = 0;
        if (!eglChooseConfig(display, configAttribs, &config, 1, &numConfigs) || numConfigs < 1) {
            LOGE("No GLES3 EGL config");
            return false;
        }

        EGLint format = 0;
        eglGetConfigAttrib(display, config, EGL_NATIVE_VISUAL_ID, &format);
        ANativeWindow_setBuffersGeometry(app->window, 0, 0, format);

        const EGLint contextAttribs[] = {
            EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL_NONE
        };

        eglBindAPI(EGL_OPENGL_ES_API);
        surface = eglCreateWindowSurface(display, config, app->window, nullptr);
        context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttribs);
        if (surface == EGL_NO_SURFACE || context == EGL_NO_CONTEXT) {
            LOGE("Failed creating EGL surface/context");
            return false;
        }

        if (!eglMakeCurrent(display, surface, surface, context)) {
            LOGE("eglMakeCurrent failed");
            return false;
        }

        eglQuerySurface(display, surface, EGL_WIDTH, &width);
        eglQuerySurface(display, surface, EGL_HEIGHT, &height);
        glViewport(0, 0, width, height);

        if (!renderer.init()) {
            LOGE("Renderer init failed");
            return false;
        }

        eglSwapInterval(display, 1);
        ready = true;
        LOGI("GLES renderer ready: %dx%d, %s", width, height, glGetString(GL_VERSION));
        return true;
    }

    void terminateDisplay() {
        ready = false;
        if (display != EGL_NO_DISPLAY) {
            if (context != EGL_NO_CONTEXT) {
                eglMakeCurrent(display, surface, surface, context);
                renderer.destroy();
            }
            eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            if (context != EGL_NO_CONTEXT) eglDestroyContext(display, context);
            if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);
            eglTerminate(display);
        }
        display = EGL_NO_DISPLAY;
        surface = EGL_NO_SURFACE;
        context = EGL_NO_CONTEXT;
        width = height = 0;
    }

    void update(float dt) {
        const float steer = std::clamp(touchSteer + keySteer, -1.0f, 1.0f);
        const float throttle = std::max(touchThrottle, keyThrottle);
        const float brake = std::max(touchBrake, keyBrake);
        vehicle.update(dt, steer, throttle, brake);
    }

    void render() {
        if (!ready || width <= 0 || height <= 0) return;

        eglQuerySurface(display, surface, EGL_WIDTH, &width);
        eglQuerySurface(display, surface, EGL_HEIGHT, &height);
        glViewport(0, 0, width, height);
        glClearColor(0.36f, 0.60f, 0.82f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        const Vec3 fwd = vehicle.forward();
        const Vec3 cameraPos = vehicle.position - fwd * 7.6f + Vec3{0.0f, 3.7f, 0.0f};
        const Vec3 cameraTarget = vehicle.position + fwd * 6.0f + Vec3{0.0f, 0.7f, 0.0f};
        const Mat4 view = lookAt(cameraPos, cameraTarget, {0.0f, 1.0f, 0.0f});
        const float aspect = static_cast<float>(width) / static_cast<float>(std::max(1, height));
        const Mat4 projection = perspective(64.0f * kPi / 180.0f, aspect, 0.08f, 400.0f);
        const Mat4 vp = multiply(projection, view);

        glUseProgram(renderer.program);
        glBindVertexArray(renderer.vao);

        // World and road.
        renderer.cube(vp, {0.0f, -0.45f, -82.0f}, {80.0f, 0.40f, 220.0f}, 0.0f, {0.16f, 0.42f, 0.14f});
        renderer.cube(vp, {0.0f, -0.18f, -82.0f}, {15.5f, 0.22f, 215.0f}, 0.0f, {0.16f, 0.17f, 0.18f});
        renderer.cube(vp, {-7.95f, 0.10f, -82.0f}, {0.30f, 0.55f, 215.0f}, 0.0f, {0.75f, 0.76f, 0.79f});
        renderer.cube(vp, { 7.95f, 0.10f, -82.0f}, {0.30f, 0.55f, 215.0f}, 0.0f, {0.75f, 0.76f, 0.79f});

        for (int i = 0; i < 22; ++i) {
            const float z = 14.0f - static_cast<float>(i) * 10.0f;
            renderer.cube(vp, {0.0f, -0.03f, z}, {0.18f, 0.03f, 4.5f}, 0.0f, {0.92f, 0.92f, 0.86f});
        }

        // Simple scenery for depth and speed perception.
        for (int i = 0; i < 28; ++i) {
            const float z = 10.0f - static_cast<float>(i) * 8.0f;
            const float hL = 1.5f + static_cast<float>((i * 37) % 7) * 0.45f;
            const float hR = 1.7f + static_cast<float>((i * 19) % 8) * 0.42f;
            renderer.cube(vp, {-13.0f, hL * 0.5f - 0.1f, z}, {3.8f, hL, 3.5f}, 0.0f, {0.56f, 0.57f, 0.60f});
            renderer.cube(vp, { 13.0f, hR * 0.5f - 0.1f, z - 3.0f}, {3.5f, hR, 3.8f}, 0.0f, {0.46f, 0.49f, 0.54f});
        }

        // Player car: body, cabin, wheels and lights.
        const Vec3 p = vehicle.position;
        const float y = vehicle.yaw;
        const Vec3 right{std::cos(y), 0.0f, std::sin(y)};
        const Vec3 forward = vehicle.forward();

        renderer.cube(vp, p, {1.85f, 0.48f, 4.15f}, y, {0.86f, 0.08f, 0.05f});
        renderer.cube(vp, p + Vec3{0.0f, 0.48f, 0.20f}, {1.55f, 0.58f, 1.85f}, y, {0.10f, 0.16f, 0.22f});
        renderer.cube(vp, p + right * -0.98f + forward * 1.18f + Vec3{0.0f,-0.26f,0.0f}, {0.32f, 0.62f, 0.78f}, y, {0.035f,0.035f,0.04f});
        renderer.cube(vp, p + right *  0.98f + forward * 1.18f + Vec3{0.0f,-0.26f,0.0f}, {0.32f, 0.62f, 0.78f}, y, {0.035f,0.035f,0.04f});
        renderer.cube(vp, p + right * -0.98f - forward * 1.18f + Vec3{0.0f,-0.26f,0.0f}, {0.32f, 0.62f, 0.78f}, y, {0.035f,0.035f,0.04f});
        renderer.cube(vp, p + right *  0.98f - forward * 1.18f + Vec3{0.0f,-0.26f,0.0f}, {0.32f, 0.62f, 0.78f}, y, {0.035f,0.035f,0.04f});
        renderer.cube(vp, p + right * -0.58f + forward * 2.09f, {0.36f,0.20f,0.12f}, y, {1.0f,0.92f,0.70f});
        renderer.cube(vp, p + right *  0.58f + forward * 2.09f, {0.36f,0.20f,0.12f}, y, {1.0f,0.92f,0.70f});

        glBindVertexArray(0);
        eglSwapBuffers(display, surface);
    }
};

int32_t handleInput(android_app* app, AInputEvent* event) {
    auto* e = static_cast<Engine*>(app->userData);
    if (!e) return 0;

    const int32_t type = AInputEvent_getType(event);
    if (type == AINPUT_EVENT_TYPE_MOTION) {
        const int action = AMotionEvent_getAction(event);
        const int masked = action & AMOTION_EVENT_ACTION_MASK;
        const int pointerUpIndex = (action & AMOTION_EVENT_ACTION_POINTER_INDEX_MASK) >> AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT;

        e->touchSteer = 0.0f;
        e->touchThrottle = 0.0f;
        e->touchBrake = 0.0f;

        if (masked != AMOTION_EVENT_ACTION_UP && masked != AMOTION_EVENT_ACTION_CANCEL) {
            const size_t count = AMotionEvent_getPointerCount(event);
            const float w = static_cast<float>(std::max(1, e->width > 0 ? e->width : ANativeWindow_getWidth(app->window)));
            const float h = static_cast<float>(std::max(1, e->height > 0 ? e->height : ANativeWindow_getHeight(app->window)));

            for (size_t i = 0; i < count; ++i) {
                if (masked == AMOTION_EVENT_ACTION_POINTER_UP && static_cast<int>(i) == pointerUpIndex) continue;
                const float x = AMotionEvent_getX(event, i);
                const float y = AMotionEvent_getY(event, i);

                if (x < w * 0.5f) {
                    const float normalized = x / (w * 0.5f);
                    e->touchSteer = std::clamp((normalized - 0.5f) * 2.0f, -1.0f, 1.0f);
                } else {
                    if (y > h * 0.50f) e->touchThrottle = 1.0f;
                    else e->touchBrake = 1.0f;
                }
            }
        }
        return 1;
    }

    if (type == AINPUT_EVENT_TYPE_KEY) {
        const int action = AKeyEvent_getAction(event);
        const int code = AKeyEvent_getKeyCode(event);
        const bool down = action == AKEY_EVENT_ACTION_DOWN;
        const float v = down ? 1.0f : 0.0f;

        switch (code) {
            case AKEYCODE_DPAD_LEFT:
            case AKEYCODE_A: e->keySteer = down ? -1.0f : (e->keySteer < 0.0f ? 0.0f : e->keySteer); return 1;
            case AKEYCODE_DPAD_RIGHT:
            case AKEYCODE_D: e->keySteer = down ? 1.0f : (e->keySteer > 0.0f ? 0.0f : e->keySteer); return 1;
            case AKEYCODE_DPAD_UP:
            case AKEYCODE_W: e->keyThrottle = v; return 1;
            case AKEYCODE_DPAD_DOWN:
            case AKEYCODE_S: e->keyBrake = v; return 1;
            default: break;
        }
    }

    return 0;
}

void handleCmd(android_app* app, int32_t cmd) {
    auto* e = static_cast<Engine*>(app->userData);
    if (!e) return;

    switch (cmd) {
        case APP_CMD_INIT_WINDOW:
            if (app->window && !e->ready) e->initDisplay();
            break;
        case APP_CMD_TERM_WINDOW:
            e->terminateDisplay();
            break;
        default:
            break;
    }
}

} // namespace

void android_main(android_app* app) {
    app_dummy();

    Engine engine{};
    engine.app = app;
    app->userData = &engine;
    app->onAppCmd = handleCmd;
    app->onInputEvent = handleInput;

    auto last = std::chrono::steady_clock::now();

    while (true) {
        int events = 0;
        android_poll_source* source = nullptr;

        while (ALooper_pollAll(engine.ready ? 0 : -1, nullptr, &events,
                               reinterpret_cast<void**>(&source)) >= 0) {
            if (source) source->process(app, source);
            if (app->destroyRequested != 0) {
                engine.terminateDisplay();
                return;
            }
        }

        if (engine.ready) {
            const auto now = std::chrono::steady_clock::now();
            float dt = std::chrono::duration<float>(now - last).count();
            last = now;
            dt = std::clamp(dt, 0.0f, 1.0f / 30.0f);
            engine.update(dt);
            engine.render();
        } else {
            last = std::chrono::steady_clock::now();
        }
    }
}
