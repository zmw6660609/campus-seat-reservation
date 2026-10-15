package com.sr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sr.pojo.entity.Reservation;
import com.sr.pojo.entity.Seat;
import com.sr.pojo.vo.AdminReservationVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReservationMapper extends BaseMapper<Reservation> {
    List<Seat> selectAvailableSeats(
            @Param("roomId") Long roomId,
            @Param("date") LocalDate date,
            @Param("slotId") Long slotId
    );

    /**
     * 管理端分页查询。第一个参数必须是 IPage（不是 Page）——
     * MyBatis-Plus 的分页插件靠「第一个参数是不是 IPage」来决定要不要自动拼 LIMIT。
     * ★ 参数写成 IPage 还有个附带好处：不用 import 具体的 Page 实现，
     *   避免把 Spring Data 的 org.springframework.data.domain.Page 和
     *   MyBatis-Plus 的 Page 搞混（两者同名，选错了就是编译期类型不兼容）。
     */
    IPage<AdminReservationVO> selectAdminPage(IPage<AdminReservationVO> page,
                                              @Param("userId") Long userId,
                                              @Param("roomId") Long roomId,
                                              @Param("reserveDate") LocalDate reserveDate,
                                              @Param("status") Integer status);

}
