package com.haucheng.erp.service;

import com.haucheng.erp.dto.CategoryRequest;
import com.haucheng.erp.entity.MdCategory;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.CategoryMapper;
import com.haucheng.erp.vo.CategoryView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class CategoryService {
    private final CategoryMapper mapper;
    private final OperationLogService operationLog;
    public List<CategoryView> tree() { Map<Long, CategoryView> values = new LinkedHashMap<>(); for (CategoryView v : mapper.selectAll()) { v.setChildren(new ArrayList<>()); values.put(v.getId(), v); } List<CategoryView> roots = new ArrayList<>(); for (CategoryView v : values.values()) { CategoryView p = v.getParentId() == null ? null : values.get(v.getParentId()); if (p == null) roots.add(v); else p.getChildren().add(v); } return roots; }
    public List<CategoryView> getAll() { return mapper.selectAll(); }
    public CategoryView getById(Long id) { CategoryView v = tree().stream().flatMap(this::flatten).filter(x -> id.equals(x.getId())).findFirst().orElse(null); if (v == null) throw BusinessException.notFound("分类不存在"); return v; }
    private java.util.stream.Stream<CategoryView> flatten(CategoryView v) { return java.util.stream.Stream.concat(java.util.stream.Stream.of(v), v.getChildren().stream().flatMap(this::flatten)); }
    @Transactional public CategoryView create(CategoryRequest r) { String c = r.getCategoryCode().trim(), n = r.getCategoryName().trim(); unique(c,n,null); validateParent(r.getParentId()); MdCategory e = build(r,c,n); mapper.insert(e); operationLog.record("CATEGORY_CREATE", String.valueOf(e.getId()), "创建分类"); return getById(e.getId()); }
    @Transactional public CategoryView update(Long id, CategoryRequest r) { require(id); if (id.equals(r.getParentId()) || (r.getParentId()!=null && mapper.subtree(id).contains(r.getParentId()))) throw BusinessException.badRequest("上级分类不能是自身或下级分类"); String c=r.getCategoryCode().trim(),n=r.getCategoryName().trim(); unique(c,n,id); validateParent(r.getParentId()); MdCategory e=build(r,c,n); e.setId(id); mapper.update(e); operationLog.record("CATEGORY_UPDATE",String.valueOf(id),"修改分类"); return getById(id); }
    @Transactional public void updateStatus(Long id, Boolean status) { require(id); mapper.update(buildStatus(id,status)); operationLog.record(status?"CATEGORY_ENABLE":"CATEGORY_DISABLE",String.valueOf(id),status?"启用分类":"停用分类"); }
    @Transactional public void delete(Long id) { require(id); if (mapper.countChildren(id)>0 || mapper.countProducts(id)>0) throw BusinessException.conflict("分类存在下级或已被物资使用，不能删除"); mapper.deleteById(id); operationLog.record("CATEGORY_DELETE",String.valueOf(id),"删除分类"); }
    private MdCategory require(Long id) { MdCategory e=mapper.selectById(id); if(e==null) throw BusinessException.notFound("分类不存在"); return e; }
    private void unique(String c,String n,Long id){if(mapper.countByCode(c,id)>0)throw BusinessException.conflict("分类编号已存在");if(mapper.countByName(n,id)>0)throw BusinessException.conflict("分类名称已存在");}
    private void validateParent(Long id){if(id!=null&&(mapper.selectById(id)==null||mapper.countActive(id)==0))throw BusinessException.badRequest("上级分类不存在或已停用");}
    private MdCategory build(CategoryRequest r,String c,String n){MdCategory e=new MdCategory();e.setParentId(r.getParentId());e.setCategoryCode(c);e.setCategoryName(n);e.setSortOrder(r.getSortOrder()==null?0:r.getSortOrder());e.setStatus(r.getStatus()==null||r.getStatus());return e;}
    private MdCategory buildStatus(Long id,Boolean status){MdCategory old=require(id);old.setStatus(status);return old;}
}
