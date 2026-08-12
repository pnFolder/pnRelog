package ru.privatenull.pnrelog.api;

/** Describes how a combat link was created or refreshed. */
public enum TagCause {
    MELEE,
    PROJECTILE,
    EXPLOSION,
    PET,
    AREA_EFFECT,
    API,
    ADMIN
}
