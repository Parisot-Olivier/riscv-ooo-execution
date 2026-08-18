import chisel3._
import chisel3.util._


//work in progress
class branch_predictor (SIZE_HISTORY: Int, NUMBER_PREDICTOR_ENTRIES: Int, NUMBER_MAX_BRANCH_FLYING: Int) extends Module {

    val io = IO(new Bundle {

        val is_branch_instruction = Input(Bool())

        
        val misspredict = Output(Bool())
        val speculative_mode = Output(Bool())
        
	})


    val history = RegInit(0.U(SIZE_HISTORY.W))
    val predict_tacken = Wire(UInt(2.W))
    val prediction_counter = Vec(NUMBER_PREDICTOR_ENTRIES, RegInit(0.U(2.W)))

	val index = UInt(log2Ceil(NUMBER_PREDICTOR_ENTRIES).W)

    val stack_branch_flying = Vec(NUMBER_MAX_BRANCH_FLYING, new Bundle {
        val valid= RegInit(0.U(Bool()))
        val taken = Reg(Bool())
        val pc = Reg(UInt(48.W))
	})

    val ptr_stack = RegInit(0.U(UInt(log2Ceil(NUMBER_MAX_BRANCH_FLYING).W)))

    predict_tacken := prediction_counter (index)

    when (io.is_branch_instruction) {
        index := pc (7,0) ^ history (index)
        branch_detected := 1
        ptr_stack := ptr_stack + 1.U
    } 

    find_pc = search_pc_in_stack (stack_branch_flying, ptr_stack)

    when (really_taken == stack_branch_flying (find_pc)) {
        
    }
}

/*
SystemVerilog associe

logic [HISTORY_WIDTH-1:0] history;
logic [1:0] prediction_counter [0:NUMBER_PREDICTOR_ENTRIES-1];
logic [47:0] branch_pc;
logic [47:0] branch_target_pc;
logic predict_taken;
logic branch_detected;

assign predict_taken = prediction_counter [index];


always_ff @(posedge clk or negedge rst_n) begin
	if (rst_n) begin
		if (is_branch_instruction) begin
			index <= pc [7:0] ^ history;
			branch_detected <= 1;
		end else begin
			branch_detected <= 0;
		end
	end else begin
		branch_detected <= 0;
		index <= 0;
	end
end

always_ff @(posedge clk or negedge rst_n) begin
	if (rst_n) begin

		if (really_taken == predicted_taken) begin
			history <= history << 1 | really_taken;

			if (!(prediction_counter [index] == 2'b11)) begin
				prediction_counter [index] + 1;
			end

		end else begin

			mispredict <= 1;
			history <= history << 1 | really_taken;

			if (!(prediction_counter [index] == 2'b11)) begin
				prediction_counter [index] <= prediction_counter [index] - 1;
			end

		end

	end else begin
		mispredict <= 0;
		for (int i =0; i<NUMBER_PREDICTOR_ENTRIES; i=i+1) begin
			prediction_counter [i] <= 0; //reset obligatoire
		end
	end
end

*/