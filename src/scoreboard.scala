import chisel3._
import chisel3.util._

//work in progress

class micro_op_stage2 extends micro_op_base {
    val issue = Bool()
    val is_issue_on_scr1 = Bool()
    val is_issue_on_scr2 = Bool()
}

class cache_iterface_bundle extends Bundle {
    val consumed = Input(Bool())
    val micro_op = Input(new micro_op_decoder)
}

class ROB_interface_bundle extends Bundle {

    val mark_dst = Valid(UInt(log2ceil(NUMBER_REGS).W))

    val entries = Vec(NUMBER_PHYSICAL_REGS, UInt(log2ceil(NUMBER_REGS).W))

    // 0=X0 -> P2 ...

}





class scoreboard (NUMBER_PHYSICAL_REGS: Int) extends Module {

    val io = IO(new Bundle {

        val bitmap = Input(Vec(NUMBER_PHYSICAL_REGS, Bool()))

        val micro_op = Input(new micro_op_renamed(SIZE_COUNTER_OP, NUMBER_REGS, DATA_WIDTH))

        val announced_read_counter = Output(Vec(NUMBER_PHYSICAL_REGS, UInt(SIZE_COUNTER.W)))

        val free_reg = Input(Vec(NUMBER_PHYSICAL_REGS, Bool()))

        val annouced_read = Output(Vec(NUMBER_PHYSICAL_REGS, UInt(log2ceil(NUMBER_PHYSICAL_REGS).W)))

        val micro_op_final = Output(new micro_op_ready)
    })

    val dependency_not_resolved = Wire(Vec(NUMBER_PHYSICAL_REGS, Bool()))
    
    val buff_announced_read_counter = RegInit(VecInit(Seq.fill(NUMBER_PHYSICAL_REGS)(0.U(SIZE_COUNTER.W))))

    for (i <- 0 until NUMBER_PHYSICAL_REGS) {

        io.bitmap (i) := buf_bitmap (i)

        //detection dependence non resolue
        when (io.bitmap (i) && !valid_data (i)) { 
            dependency_not_resolved (i) := true.B
        } .otherwise {
            dependency_not_resolved (i) := false.B
        }

    }

    val buf_micro_op_final = Reg(new micro_op_ready(SIZE_COUNTER_OP, NUMBER_REGS, DATA_WIDTH))

    //mark issue

    buf_micro_op_final := io.micro_op
    buf_micro_op_final.is_issue_on_scr1 := dependency_not_resolved (buf_micro_op.src1)
    buf_micro_op_final.is_issue_on_scr2 := dependency_not_resolved (buf_micro_op.src2)

    buf_micro_op_final.issue := dependency_not_resolved (buf_micro_op.src1) | dependency_not_resolved (buf_micro_op.src2)


    val valid_inc_src1 = RegInit(false.B)
    val valid_inc_src2 = RegInit(false.B)
    
    val inc_annouced_read_src1 = RegInit(0.U(log2ceil(NUMBER_PHYSICAL_REGS).W))
    val inc_annouced_read_src2 = RegInit(0.U(log2ceil(NUMBER_PHYSICAL_REGS).W))

    when (buf_micro_op_final.valid) {
        valid_inc_src1 := buf_micro_op_final.valid_src1
        valid_inc_src2 := buf_micro_op_final.valid_src2
        io.micro_op_final := buf_micro_op_final
        
        //section preuve:
        //pas de probleme de annouced_read > served_read car au rochain cycle on est sûr 
        //que l'instruction ne va pas etre executer
        inc_annouced_read_src1 := buf_micro_op_final.scr1
        inc_annouced_read_src2 := buf_micro_op_final.scr2

    } .otherwise {
        valid_inc := false.B
        ecall_inst := false.B
    }


    //merged update annouced read

//section preuve:
//voir preuve 1 dans preuves.md: les deux cas du when sont distincts et annonced_read_counter doit 
//avoir un seul driver donc un seul when
    for (i <- 0 until NUMBER_PHYSICAL_REGS) {
        when ((inc_annouced_read_src1 === i && valid_inc_src1) | (inc_annouced_read_src2 === i && valid_inc_src2))  {
            //section preuve:
            //voir preuve 2, pas besoin de gestion du debordement du compteur
            announced_read_counter (i) := announced_read_counter (i) + 1.U 
        } .elsewhen (free_reg (i)) {
            announced_read_counter (i) := 0.U
        } 
    }
}






class allocation (NUMBER_PHYSICAL_REGS: Int) extends Module {

    val io = IO(new Bundle {
        val free_reg = Input(Vec(NUMBER_PHYSICAL_REGS, Bool()))
        val ROB = new ROB_interface_bundle
        val cache = new cache_iterface_bundle

        val micro_op_allocated = Output(new micro_op_allocated)

        val search_PR = new Bundle {
            val allocated_PR = Input(UInt(log2ceil(NUMBER_REG).W))
            val consumed = Output(Bool())
        }
    })

    val granted_physical_reg = Reg(Valid(Vec(NUMBER_PHYSICAL_REGS, Bool())))
    val buf_bitmap = RegInit(VecInit(Seq.fill(NUMBER_PHYSICAL_REGS)(false.B)))

    //gestion de la bitmap
    for (i <- 0 until NUMBER_PHYSICAL_REGS) {
        io.bitmap (i) := buf_bitmap (i)

        when (io.free_reg (i)) {
            buf_bitmap (i) := false.B
        } .elsewhen (granted_physical_reg.bits(i) && granted_physical_reg.valid) {
            buf_bitmap (i) := true.B
        }

    }


    //get micro op from cache
    val buf_micro_op = Reg(new micro_op_decoder (SIZE_COUNTER_OP, NUMBER_REGS, DATA_WIDTH))

    when (io.cache.micro_op.valid) {
        buf_micro_op := io.cache.micro_op
        io.cache.consumed := true.B
    } .otherwise {
        io.cache.consumed := false.B
    }

    // Récupère depuis la ROB le registre physique actuellement associé au registre architectural,
    // puis met à jour le mapping de ce registre architectural avec le registre physique `dst` de la micro-opération.

    val buf_micro_op = Reg(new micro_op_renamed (SIZE_COUNTER_OP, NUMBER_REGS, DATA_WIDTH))

    when (buf_micro_op.valid) {
        buf_micro_op := buf_micro_op

        buf_micro_op.scr1 := ROB.entries (src1)
        buf_micro_op.scr2 := ROB.entries (src2)

        io.ROB.mark_dst.bits := buf_micro_op.dst
        io.ROB.mark_dst.valid  := 1
    } .otherwise {
        buf_micro_op.valid := 0
        io.ROB.mark_dst.valid  := 0
    }

    //alloc new reg 

    when (buf_micro_op_stage1.valid) {
        io.micro_op_allocated := buf_micro_op
        io.micro_op_allocated.dst := io.search_PR.allocated_PR
        
        granted_physical_reg (io.search_PR.allocated_PR) := true.B

        io.search_PR.consumed := true.B
        io.search_PR.cosumed := true.B
    } .otherwise {
        io.search_PR.consumed := false.B
        granted_physical_reg (io.search_PR.allocated_PR) := false.B
        io.micro_op_allocated.valid := false.B
    }
}

//def search_PR 
// when (consumed) {
// schearch new }

class alloc_and_scoring () extends Module{

    val io = IO(new Bundle{
        val ROB = new ROB_interface_bundle
        val bitmap = Output(Vec(NUMBER_PHYSICAL_REGS, Bool()))
        val free_reg = Input(Vec(NUMBER_PHYSICAL_REGS, Bool()))
        val announced_read_counter = Output(Vec(NUMBER_PHYSICAL_REGS, UInt(SIZE_COUNTER.W)))
    })

    val buf_bitmap = RegInit(VecInit(Seq.fill(NUMBER_PHYSICAL_REGS)(false.B)))

    val alloc_PR = search_PR(buf_bitmap, alloc.io.search_PR.consumed)

    alloc.io.search_PR.allocated_PR := alloc_PR

    val alloc = allocation(NUMBER_PHYSICAL_REGS)
    val scoring = scoreboard ()
}
