package app.morphe.patches.mapsautomotive

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import java.util.logging.Logger

private const val EV_PRODUCER = "Lifd;"
private const val EV_EXT = "Lapp/morphe/extension/mapsautomotive/patches/NatNativeEvLayer;"
private val evLogger = Logger.getLogger("NAT.NativeEVCS")

/** Exact inspected build only. Optional: never force a different renderer or skip native eligibility. */
internal fun BytecodePatchContext.useNatNativeEvLayer() {
    requireExtensionMethod(EV_EXT, "gate", listOf("Ljava/lang/Object;", "Z"), "Z")
    val producer = classDefByOrNull(EV_PRODUCER) ?: return evLogger.warning("EVCS producer unavailable")
    val expected = mapOf("a" to "Ljava/util/concurrent/Executor;", "n" to "Lfut;", "q" to "Ljava/lang/Boolean;")
    if (!expected.all { (name, type) -> producer.fields.any { it.name == name && it.type == type } }) {
        evLogger.warning("EVCS producer fields changed"); return
    }
    val trace = producer.methods.any { method ->
        method.implementation?.instructions?.any {
            ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "EvcsVisualizationContentProducer:"
        } == true
    }
    if (!trace) { evLogger.warning("EVCS dump anchor unavailable"); return }
    val method = producer.methods.singleOrNull { it.name == "b" && it.returnType == "V" && it.parameterTypes.isEmpty() && it.implementation != null }
    if (method == null) { evLogger.warning("EVCS native refresh unavailable"); return }
    val impl = method.implementation ?: return
    val code = impl.instructions.toList()
    val self = impl.registerCount - 1
    val sites = code.indices.filter { i ->
        val call = code[i] as? FiveRegisterInstruction
        val ref = (code[i] as? ReferenceInstruction)?.reference as? MethodReference
        val move = code.getOrNull(i+1) as? OneRegisterInstruction
        val store = code.getOrNull(i+2) as? TwoRegisterInstruction
        val f = (code.getOrNull(i+2) as? ReferenceInstruction)?.reference as? FieldReference
        call != null && code[i].opcode == Opcode.INVOKE_STATIC && call.registerCount == 1 &&
            ref?.definingClass == "Ljava/lang/Boolean;" && ref.name == "valueOf" &&
            ref.parameterTypes.map { it.toString() } == listOf("Z") && ref.returnType == "Ljava/lang/Boolean;" &&
            move != null && code[i+1].opcode == Opcode.MOVE_RESULT_OBJECT &&
            store != null && code[i+2].opcode == Opcode.IPUT_OBJECT && store.registerA == move.registerA &&
            store.registerB == self && f?.definingClass == EV_PRODUCER && f.name == "q" &&
            self in 0..15 && call.registerC in 0..15 && call.registerC != self
    }
    val index = sites.singleOrNull()
    if (index == null) { evLogger.warning("EVCS eligibility anchor ambiguous: ${sites.size}"); return }
    val reg = (code[index] as FiveRegisterInstruction).registerC
    val mutable = mutableMethodOf(method)
    val hook = ImmutableMethodReference(EV_EXT, "gate", listOf("Ljava/lang/Object;", "Z"), "Z")
    // Replace the branch target itself, so all native predecessor paths execute the hook.
    mutable.replaceInstruction(index, BuilderInstruction35c(Opcode.INVOKE_STATIC, 2, self, reg, 0, 0, 0, hook))
    mutable.addInstructions(index+1, """
        move-result v$reg
        invoke-static {v$reg}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
    """.trimIndent())
    evLogger.info("EVCS MAIN native gate installed at instruction $index")
}
