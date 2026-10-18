/**
 * 操作台和 Settings Screen 由前端负责。本包故意留空。
 * <p>
 * 前端只依赖 {@link dev.baritoneautomate.bridge.BaritoneGateway}、
 * {@link dev.baritoneautomate.automate.RunController}、
 * {@link dev.baritoneautomate.combat.CombatSession}、
 * {@link dev.baritoneautomate.inventory.InventoryAccess}。
 * 不要在界面代码里直接调用 {@code baritone.api}。
 * 热键注册在 {@link dev.baritoneautomate.bootstrap.KeybindStubs}，
 * 用 {@code setOpenControlsHook} 接 Screen。
 */
package dev.baritoneautomate.ui;
