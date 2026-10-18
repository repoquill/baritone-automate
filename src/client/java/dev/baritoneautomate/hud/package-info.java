/**
 * HUD（阶段、Process、ETA、战斗中）由前端负责，用客户端 Hud 回调绘制。
 * <p>
 * 读 {@link dev.baritoneautomate.automate.RunController#snapshot()}、
 * {@link dev.baritoneautomate.bridge.BaritoneGateway#observe()}、
 * {@link dev.baritoneautomate.combat.CombatSession#snapshot()}。
 * ETA 来自 Baritone 的估算 tick，不是墙钟。
 */
package dev.baritoneautomate.hud;
