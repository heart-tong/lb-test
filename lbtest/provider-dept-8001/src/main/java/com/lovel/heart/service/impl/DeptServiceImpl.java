package com.lovel.heart.service.impl;

import com.lovel.heart.dao.DeptDao;
import com.lovel.heart.entities.Dept;
import com.lovel.heart.service.DeptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service

public class DeptServiceImpl implements DeptService {

    private final DeptDao dao;

    @Autowired
    public DeptServiceImpl(DeptDao dao) {
        this.dao = dao;
    }


    @Override

    public boolean add(Dept dept) {

        return dao.addDept(dept);

    }

    @Override

    public Dept get(Long id) {

        return dao.findById(id);

    }

    @Override

    public List<Dept> list() {

        return dao.findAll();

    }

}