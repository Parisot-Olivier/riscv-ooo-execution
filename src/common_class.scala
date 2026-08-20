import chisel3._
import chisel3.util._

//work in progress

object shift_e extends ChiselEnum {
    val shift_8, shift_16, shift_32, shift_48 = value
}

object cond_e extends ChiselEnum {
    val if_zero, if_true, if_not_true = value
}

class mov_family_bundle extends Bundle {

}

class jump_family_bundle extends Bundle {
    val branch_pc = UInt(48.W)
    val else_pc = UInt(48.W)
    val cond = new cond_e
}

class ecall_family_bundle extends Bundle {
    //copie de la ROB a l'etat architectural du pc courant au moment de l'allocation
}

class shift_family_bundle extends Bundle {
    
}


//assert SIZE_COUNTER_OP > NUMBER_ENTRIES * NUMBER_FIFO ;
class micro_op_common (SIZE_COUNTER_OP:Int, NUMBER_REGS:Int, DATA_WIDTH:Int, MAX_BRANCH_FLYING: Int) extends Bundle {

    val valid = Bool()

    //dès que le decodeur tombe sur un brénchement il marque toutes les instructions qu'il decode en predictive
    val is_predictive = Bool()
    //permet de nettoyer en cas de misspredict les bon niveau de branchement, 
    //il sert de pointeur de la stack dans le predicteur de branchement
    val branch_depth = UInt(log2ceil(MAX_BRANCH_FLYING).W)
    
    val counter_op = UInt(SIZE_COUNTER_OP.W)

    val is_mov_family = Bool()
    val is_jump_family = Bool()
    val is_ecall_family = Bool()

    val has_shift = Bool()

    val imm = UInt(DATA_WIDTH.W)

    val shift_requested = new shift_e


    val mov_family = new mov_family_bundle
    val jump_family = new jump_family_bundle
    val ecall_family = new ecall_family_bundle
    val shift_family = new shift_family_bundle
}

class micro_op_from_decoder (SIZE_COUNTER_OP:Int, NUMBER_REGS:Int, DATA_WIDTH:Int, MAX_BRANCH_FLYING: Int) extends micro_op_common {
    val valid_src1 = Bool()
    val valid_src2 = Bool()
    val scr1 = UInt(log2ceil(NUMBER_REGS).W)
    val scr2 = UInt(log2ceil(NUMBER_REGS).W)
    val dst = UInt(log2ceil(NUMBER_REGS).W)
}

class micro_op_renamed (SIZE_COUNTER_OP:Int, NUMBER_REGS:Int, DATA_WIDTH:Int, MAX_BRANCH_FLYING: Int) extends micro_op_common {
    val valid_src1 = Bool()
    val valid_src2 = Bool()
    val source_physical_reg_1 = UInt(log2ceil(NUMBER_PHYSICAL_REGS).W)
    val source_physical_reg_2 = UInt(log2ceil(NUMBER_PHYSICAL_REGS).W)
    val destination_physical_reg = UInt(log2ceil(NUMBER_PHYSICAL_REGS).W)
}


class micro_op_ready (SIZE_COUNTER_OP:Int, NUMBER_PHYSICAL_REGS:Int, DATA_WIDTH:Int, MAX_BRANCH_FLYING: Int) extends micro_op_renamed {
    val issue = Bool()
    val is_issue_on_scr1 = Bool()
    val is_issue_on_scr2 = Bool()
}

