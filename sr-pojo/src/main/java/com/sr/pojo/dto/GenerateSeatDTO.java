package com.sr.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateSeatDTO {

    @NotNull(message = "自习室ID不能为空")
    private Long studyRoomId;

    /** 行数：1 ~ 26。上限是 26 因为行号用字母拼（'A' + i - 1），再多就拼出怪字符了 */
    @NotNull(message = "行数不能为空")
    @Min(value = 1, message = "行数至少为1")
    @Max(value = 26, message = "行数最多26（行号只到 A~Z）")
    private Integer rowCount;

    /** 列数：1 ~ 99 */
    @NotNull(message = "列数不能为空")
    @Min(value = 1, message = "列数至少为1")
    @Max(value = 99, message = "列数最多99")
    private Integer colCount;
}
