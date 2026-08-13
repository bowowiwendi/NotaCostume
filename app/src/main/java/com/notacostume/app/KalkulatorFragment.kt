package com.notacostume.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.notacostume.app.databinding.FragmentKalkulatorBinding
import java.util.Locale

class KalkulatorFragment : Fragment() {

    private var _b: FragmentKalkulatorBinding? = null
    private val b get() = _b!!

    private var acc: Double? = null
    private var op: Char? = null
    private var current = "0"
    private var fresh = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentKalkulatorBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listOf(
            b.btn0 to '0', b.btn1 to '1', b.btn2 to '2', b.btn3 to '3', b.btn4 to '4',
            b.btn5 to '5', b.btn6 to '6', b.btn7 to '7', b.btn8 to '8', b.btn9 to '9',
            b.btnDot to '.'
        ).forEach { (btn, c) -> btn.setOnClickListener { digit(c) } }

        b.btnPlus.setOnClickListener { setOp('+') }
        b.btnMinus.setOnClickListener { setOp('-') }
        b.btnMul.setOnClickListener { setOp('×') }
        b.btnDiv.setOnClickListener { setOp('÷') }
        b.btnEq.setOnClickListener { equals() }
        b.btnClear.setOnClickListener { clear() }
        b.btnBack.setOnClickListener { backspace() }
        b.btnPct.setOnClickListener { percent() }

        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun digit(c: Char) {
        if (current == "Error") {
            current = "0"
            fresh = true
        }
        if (fresh) {
            current = if (c == '.') "0." else c.toString()
            fresh = false
        } else {
            if (c == '.' && current.contains('.')) return
            if (c != '.' && current == "0") {
                current = c.toString()
            } else {
                current = (current + c).take(16)
            }
        }
        refresh()
    }

    private fun setOp(next: Char) {
        if (current == "Error") return
        val value = current.toDoubleOrNull() ?: 0.0
        if (acc == null) {
            acc = value
        } else if (!fresh && op != null) {
            acc = compute(acc!!, op!!, value)
        }
        op = next
        fresh = true
        refresh()
    }

    private fun equals() {
        if (current == "Error") return
        val accNow = acc ?: current.toDoubleOrNull() ?: 0.0
        val value = current.toDoubleOrNull() ?: 0.0
        val result = if (op != null) compute(accNow, op!!, value) else value
        current = format(result)
        acc = null
        op = null
        fresh = true
        refresh()
    }

    private fun clear() {
        acc = null
        op = null
        current = "0"
        fresh = true
        refresh()
    }

    private fun backspace() {
        if (current == "Error") {
            clear()
            return
        }
        if (fresh) return
        current = if (current.length > 1) current.dropLast(1) else "0"
        if (current == "-" || current == "") current = "0"
        refresh()
    }

    private fun percent() {
        if (current == "Error") return
        current = format((current.toDoubleOrNull() ?: 0.0) / 100.0)
        fresh = true
        refresh()
    }

    private fun compute(a: Double, o: Char, c: Double): Double =
        when (o) {
            '+' -> a + c
            '-' -> a - c
            '×' -> a * c
            '÷' -> if (c == 0.0) Double.NaN else a / c
            else -> a
        }

    private fun refresh() {
        val a = acc?.let(::format) ?: ""
        b.tvPrev.text = if (op != null) "$a $op" else a
        b.tvDisplay.text = if (fresh && acc != null && op != null) format(acc!!) else current
    }

    private fun format(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "Error"
        if (v == Math.floor(v) && Math.abs(v) < 1e15) {
            return v.toLong().toString()
        }
        val s = String.format(Locale.US, "%.10f", v).trimEnd('0').trimEnd('.')
        return if (s == "-0") "0" else s
    }
}
