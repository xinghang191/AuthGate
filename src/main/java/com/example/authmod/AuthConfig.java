package com.example.authmod;

import net.minecraftforge.common.ForgeConfigSpec;

public final class AuthConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> BIND_BY;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_LOGIN_IN_SINGLE_PLAYER;
    public static final ForgeConfigSpec.IntValue AUTH_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_ATTEMPTS;
    public static final ForgeConfigSpec.IntValue LOCKOUT_SECONDS;
    public static final ForgeConfigSpec.BooleanValue RELOCK_ON_DEATH;
    public static final ForgeConfigSpec.BooleanValue RELOCK_ON_DIMENSION_CHANGE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("authentication");
        BIND_BY = builder.define("bindBy", "UUID",
            (Object value) -> value instanceof String bindBy
                && ("UUID".equalsIgnoreCase(bindBy) || "NAME".equalsIgnoreCase(bindBy)));
        REQUIRE_LOGIN_IN_SINGLE_PLAYER = builder.define("requireLoginInSinglePlayer", false);
        AUTH_TIMEOUT_SECONDS = builder.defineInRange("authTimeoutSeconds", 180, 0, 86_400);
        MAX_ATTEMPTS = builder.defineInRange("maxAttempts", 5, 1, 100);
        LOCKOUT_SECONDS = builder.defineInRange("lockoutSeconds", 300, 0, 86_400);
        RELOCK_ON_DEATH = builder.define("relockOnDeath", false);
        RELOCK_ON_DIMENSION_CHANGE = builder.define("relockOnDimensionChange", false);
        builder.pop();
        SPEC = builder.build();
    }

    private AuthConfig() {
    }
}
