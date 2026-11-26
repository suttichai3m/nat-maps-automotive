package app.morphe.patches.mapsautomotive

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import java.util.logging.Logger

// Exact DEX anchors from Google Maps Automotive 26.10.120003.E (1068833413).
// Lkrj contains ETA, distance and batteryOnArrival; Lhdp invokes Lcmp.v for that model.
// The native Compose renderer owns the battery vector and formatted Google SOC text.
// No View-tree guessing, no invented battery value. Fail closed on an unfamiliar APK.
private const val RENDERER = "Lcmp;"
private const val RENDER_CALL = "Lcmp;->v(Lqut;ZLkbn;Lciu;Lbmh;ZLbbj;I)V"
private const val ICON_SIZE = "Lanv;->c(Lbmh;F)Lbmh;"
private const val ICON_DRAW = "Lskk;->g(Lbsm;Ljava/lang/String;Lbmh;JLbbj;II)V"
private const val TEXT_DRAW = "Lcom/google/android/libraries/mdi/search/g3appsearch/app/\$\$__AppSearch__G3AttributionInfo;->c(Ljava/lang/String;Lbmh;JJLckp;JLcmf;JIZIILaofi;Lciu;Lbbj;III)V"
private const val ROW_SEMANTICS = "Lnnh;->b(Lbmh;Lrqu;)Lbmh;"
private const val NATIVE_CARD = "Lapp/morphe/extension/mapsautomotive/patches/NatNativeCard;"
private val nativeBatteryLogger = Logger.getLogger("NAT.NativeBattery")

internal fun BytecodePatchContext.useNativeNavigationBattery() {
    requireExtensionMethod(NATIVE_CARD, "decorate", listOf("Ljava/lang/Object;", "Ljava/lang/Object;"), "Ljava/lang/Object;")
    requireExtensionMethod(NATIVE_CARD, "boldWeight", emptyList(), "Ljava/lang/Object;")
    // Every reflected native member must exist in this APK before injecting the decoration.
    val members = listOf(
        "Lanv;->f(Lbmh;FFI)Lbmh;", "Lqp;->f(Lbmh;FFFF)Lbmh;",
        "Lqn;->c(Lbmh;Laofi;)Lbmh;", "Laofi;->a(Ljava/lang/Object;)Ljava/lang/Object;",
        "Lqut;->b()Z", "Lcaq;->m()J", "Lcaq;->a()F", "Lcaq;->y(JJJJLsp;F)V",
        "Lckp;-><init>(I)V", "Lcaq;->o()Lbrv;", "Lbrv;->b()Lbpu;"
    )
    for (key in members) {
        val owner = key.substringBefore("->")
        if (classDefByOrNull(owner)?.methods?.none { m ->
                m.definingClass + "->" + m.name + m.parameterTypes.joinToString("", "(", ")") + m.returnType == key
            } != false) throw PatchException("NAT18: native card member missing: $key")
    }
    for (key in listOf("Lqut;->b:Lqus;", "Lqus;->f:I", "Lbsa;->a:Lbsa;", "Laobo;->a:Laobo;", "Lbpf;->a:Landroid/graphics/Canvas;")) {
        val owner = key.substringBefore("->")
        if (classDefByOrNull(owner)?.fields?.none { f -> f.definingClass + "->" + f.name + ":" + f.type == key } != false)
            throw PatchException("NAT18: native card field missing: $key")
    }
    val cls = classDefByOrNull(RENDERER) ?: throw PatchException("NAT: native battery renderer not found")
    val caller = classDefByOrNull("Lhdp;") ?: throw PatchException("NAT: trip summary renderer not found")
    if (caller.methods.count { m -> m.implementation?.instructions?.any { memberKey(it) == RENDER_CALL } == true } != 1)
        throw PatchException("NAT: trip summary battery call changed")
    val original = cls.methods.singleOrNull { it.name == "v" &&
        it.parameterTypes.map { p -> p.toString() } == listOf("Lqut;", "Z", "Lkbn;", "Lciu;", "Lbmh;", "Z", "Lbbj;", "I") && it.returnType == "V" }
        ?: throw PatchException("NAT: battery renderer signature changed")
    val method = mutableClassDefBy(cls).methods.single { it.name == original.name && it.parameterTypes == original.parameterTypes && it.returnType == original.returnType }
    val impl = method.implementation ?: throw PatchException("NAT: missing battery renderer body")
    if (impl.registerCount != 51) throw PatchException("NAT: unexpected native battery registers")
    val code = impl.instructions.toList()
    fun unique(key: String): Int = code.indices.singleOrNull { memberKey(code[it]) == key }
        ?: throw PatchException("NAT: ambiguous or missing native call $key")
    val row = unique(ROW_SEMANTICS)
    val rowResult = code.getOrNull(row + 1)
    val rowCall = code[row] as? FiveRegisterInstruction
    if (rowCall?.registerCount != 2 || rowCall.registerC != 14 || rowCall.registerD != 6 ||
        rowResult?.opcode != Opcode.MOVE_RESULT_OBJECT || (rowResult as? OneRegisterInstruction)?.registerA != 6 ||
        code.getOrNull(row + 2)?.location?.labels?.isNotEmpty() != false ||
        !(maxOf(0, row - 12) until row).any { memberKey(code[it]) == "Lqut;->c:Ljava/lang/String;" })
        throw PatchException("NAT18: native row/forecast registers changed")
    val icon = unique(ICON_SIZE)
    val draw = unique(ICON_DRAW)
    val text = unique(TEXT_DRAW)
    val iconCall = code[icon] as? FiveRegisterInstruction ?: throw PatchException("NAT: icon call format changed")
    val textCall = code[text] as? RegisterRangeInstruction ?: throw PatchException("NAT: text call format changed")
    val oldSize = code.getOrNull(icon - 2)
    if (iconCall.registerCount != 2 || iconCall.registerD != 6 || oldSize?.opcode != Opcode.CONST_HIGH16 ||
        (oldSize as? OneRegisterInstruction)?.registerA != 6 || (oldSize as? NarrowLiteralInstruction)?.narrowLiteral != 0x41c00000 ||
        draw <= icon || text <= draw || textCall.startRegister != 3 || textCall.registerCount != 22 ||
        code[icon].location.labels.isNotEmpty() || code[text].location.labels.isNotEmpty())
        throw PatchException("NAT: native icon/text anchor layout changed")
    // Last parameter is Compose default mask. Bits 3 and 4 default fontSize and fontWeight.
    val mask = (maxOf(0, text - 48) until text).any { i ->
        val insn = code[i]
        insn.opcode == Opcode.CONST && (insn as? OneRegisterInstruction)?.registerA == 24 &&
            (insn as? NarrowLiteralInstruction)?.narrowLiteral == 0x1affa
    }
    if (!mask) throw PatchException("NAT: native text default mask changed")
    // Explicit 50sp packed TextUnit: type 1 (Sp), IEEE-754 float bits for 50.0.
    // Retain native text, colors, semantics and user font scaling.
    method.addInstructions(text, """
        const-wide v7, 0x0000000142480000L
        invoke-static {}, $NATIVE_CARD->boldWeight()Ljava/lang/Object;
        move-result-object v9
        check-cast v9, Lckp;
        and-int/lit8 v24, v24, -0x19
    """.trimIndent())
    method.addInstructions(icon, "const/high16 v6, 0x42280000") // 42 dp icon, from 24 dp
    // v6 is the row's original semantics modifier, v1 the same Qut forecast used by the native text.
    // Add decoration before Compose measures the row. No View hierarchy scan or overlay.
    method.addInstructions(row + 2, """
        invoke-static { v6, v1 }, $NATIVE_CARD->decorate(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object v6
        check-cast v6, Lbmh;
    """.trimIndent())
    nativeBatteryLogger.info("NAT18 native battery card patched: $RENDER_CALL, min=220x124dp, icon=42dp, text=50sp bold")
}
