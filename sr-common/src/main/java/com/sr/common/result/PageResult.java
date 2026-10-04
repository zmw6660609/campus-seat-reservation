package com.sr.common.result;

import lombok.Data;
import java.io.Serializable;
import java.util.List;
import java.util.Collections;
@Data
public class PageResult<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long total;
    private List<T> records;
    public static <T> PageResult<T> of(Long total, List<T> records) {
        PageResult<T> pageResult = new PageResult<>();
        pageResult.setTotal(total);
        pageResult.setRecords(records == null ? Collections.emptyList() : records);
        return pageResult;
    }

}
