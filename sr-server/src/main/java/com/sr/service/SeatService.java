package com.sr.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sr.pojo.dto.GenerateSeatDTO;
import com.sr.pojo.entity.Seat;

public interface SeatService extends IService<Seat> {
    void batchGenerateSeat(GenerateSeatDTO dto);
}
