import chisel3._
import chisel3.util._

//doit etre tester

class info_case_physical_reg (SIZE_COUNTER: Int) extends Bundle {

    val bitmap = Bool() //from scoreboard
    val is_valid_data = Bool() //from physical_regs
    val is_architectural_reg = Bool() // from ROB
    
    val counter_op_served = UInt(SIZE_COUNTER.W) //from physical_regs
    val counter_op_asked = UInt(SIZE_COUNTER.W) //from scoreboards
}

class scheduler (NUMBER_PHYSICAL_REGS: Int, SIZE_COUNTER: Int) extends Module {

    val io = IO(new Bundle {


        val info_PR = Input(Vec (NUMBER_PHYSICAL_REGS, new info_case_physical_reg(SIZE_COUNTER)))

        val free_reg = Output(Vec (NUMBER_PHYSICAL_REGS, Bool())) // vers scoreboard et physical regs

        val wake_up = Output(Vec (NUMBER_PHYSICAL_REGS, Bool())) //vers issue queue
    })

    //synchronisation etat scoreboard et physical regfile


    //pas besoin de init je pense, car reset via des reset des signaux qu'il prend: WireInit(VecInit(Seq.fill(N)(false.B)))
    val is_releasable = Wire(Vec(NUMBER_PHYSICAL_REGS, Bool()))
    
    val buf_free_reg = RegInit(VecInit(Seq.fill(NUMBER_PHYSICAL_REGS)(false.B)))


    for (i <- 0 until NUMBER_PHYSICAL_REGS) {
        
        is_releasable (i) := io.info_PR(i).is_valid_data && io.info_PR(i).bitmap && !io.info_PR(i).is_architectural_reg && (io.info_PR(i).counter_op_asked == io.info_PR(i).counter_op_served)
        //est ce que garder bitmap en condition, car bitmap => valid_data
        io.free_reg (i) := buf_free_reg (i)

        when (is_releasable (i)) {
            buf_free_reg (i) := true.B
        } .otherwise {
            buf_free_reg (i) := false.B
        }

    }

    //wake-up 

    //pas besoin de init je pense, car reset via des reset des signaux qu'il prend: WireInit(VecInit(Seq.fill(N)(false.B)))
    val is_dependency_resolved = Wire(Vec(NUMBER_PHYSICAL_REGS, Bool()))

    val buf_wake_up = RegInit(VecInit(Seq.fill(NUMBER_PHYSICAL_REGS)(false.B)))

    for (i <- 0 until NUMBER_PHYSICAL_REGS) {
        io.wake_up (i) := buf_wake_up (i)
        is_dependency_resolved (i) := io.info_PR(i).bitmap && io.info_PR(i).is_valid_data
        //est ce que garder bitmap en condition, car bitmap => valid_data
        when (is_dependency_resolved (i)) {
            buf_wake_up (i) := true.B
        } .otherwise {
            buf_wake_up (i) := false.B
        }

    }

}

/*
SystemVerilog associé voulu:

logic is_releasable [0:NUMBER_PHYSICAL_REGS-1];
logic wake_up [0:NUMBER_PHYSICAL_REGS-1];
logic is_dependency_resolved [0:NUMBER_PHYSICAL_REGS-1];

generate
	for (genvar i = 0; i < NUMBER_PHYSICAL_REGS-1; i = i + 1) begin
		assign is_releasable[i] = is_data_valid[i] && (announced_reader_counter[i] == served_reader_counter[i]) && (is_mapping_overwritten[i] | is_captured_by_rob[i]);
		
		assign is_dependency_resolved [i] = valid [i] && is_mapping_overwritten [i];
		
		always_ff @(posedge clk or negedge rst_n) begin
			if (rst_n) begin
				if (is_dependency_resolved [i]) begin
					wake_up [i] <= 1;
				end
			end else begin
				wake_up [i] <= 0;
			end
		end
		
		always_ff @(posedge clk or negedge rst_n) begin
			if (rst_n) begin
				if (is_releasable [i]) begin // is_data_valid : Physical regs ; is_mapping_overwritten : Scoreboard.
												  // On centralise la décision de libérer le registre physique.
					release_physical_reg [i] <= 1;
				end else begin
					release_physical_reg [i] <= 0;
				end
			end else begin
			end
		end
	end
endgenerate

*/