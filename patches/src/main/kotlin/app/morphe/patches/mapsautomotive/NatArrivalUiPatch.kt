package app.morphe.patches.mapsautomotive

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch

private const val NAT_UI = "Lapp/morphe/extension/mapsautomotive/patches/NatArrivalUi;"

@Suppress("unused")
val natArrivalUiPatch = bytecodePatch(
    name = "NAT arrival battery V2.1",
    description = "Draws a larger arrival percentage card with a charge bar in native text views. Does not change the forecast.",
    default = true,
) {
    compatibleWith(compatibility)
    dependsOn(bydBatteryPatch)
    execute {
        requireExtensionMethod(NAT_UI, "install", listOf("Landroid/app/Activity;"), "V")
        val method = findHierarchyHook(MAIN_CLASS, "Maps activity onCreate") {
            it.name == "onCreate" && it.returnType == "V" &&
                it.parameterTypes.map { type -> type.toString() } == listOf("Landroid/os/Bundle;")
        }
        method.addInstruction(0, "invoke-static/range { p0 .. p0 }, $NAT_UI->install(Landroid/app/Activity;)V")
    }
}
