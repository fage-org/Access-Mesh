package cn.ac.fage.accessmesh.example.data.csv;

import cn.ac.fage.accessmesh.example.data.catalog.DemoField;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Collections;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import tools.jackson.databind.node.JsonNodeFactory;

/** Bounded input parser; errors never contain raw record values. */
public final class DemoCsvReader {
    public static final int MAX_BYTES=10*1024*1024;
    public Parsed read(byte[] bytes) {
        if(bytes.length>MAX_BYTES) throw limit();
        String text;
        try { text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch(java.nio.charset.CharacterCodingException e) { throw invalid(1,"UTF-8 编码无效"); }
        if(text.startsWith("\uFEFF")) text=text.substring(1);
        var lines=parse(text);
        if(lines.size()<2) throw invalid(1,"缺少表头或业务记录");
        List<DemoField> fields=new ArrayList<>();
        try { for(String key:lines.getFirst()) fields.add(DemoField.require(key)); }
        catch(BizException e) { throw invalid(1,"包含未知字段"); }
        var mandatory=EnumSet.allOf(DemoField.class); mandatory.remove(DemoField.REMARK);
        if(new HashSet<>(fields).size()!=fields.size() || !fields.containsAll(mandatory)) throw invalid(1,"重复表头或缺少必填字段");
        List<Map<DemoField,Object>> records=new ArrayList<>();
        for(int row=1;row<lines.size();row++) {
            var cells=lines.get(row);
            if(cells.size()!=fields.size()) throw invalid(row+1,"列数与表头不一致");
            Map<DemoField,Object> values=new EnumMap<>(DemoField.class);
            for(int column=0;column<fields.size();column++) {
                var field=fields.get(column); String cell=cells.get(column);
                if(field==DemoField.REMARK && cell.isEmpty()) { values.put(field,null); continue; }
                if(cell.isBlank()) throw invalid(row+1,field.key()+" 不能为空");
                try {
                    var node=switch(field.kind()) {
                        case STRING, DATE -> JsonNodeFactory.instance.textNode(cell);
                        case USER_ID -> {
                            if(!cell.matches("[0-9]+")) throw new NumberFormatException();
                            yield JsonNodeFactory.instance.numberNode(Long.parseLong(cell));
                        }
                        case DECIMAL -> JsonNodeFactory.instance.numberNode(new java.math.BigDecimal(cell));
                    };
                    values.put(field,field.literal(node));
                } catch(IllegalArgumentException|BizException e) { throw invalid(row+1,field.key()+" 类型或长度无效"); }
            }
            records.add(Collections.unmodifiableMap(values));
        }
        return new Parsed(List.copyOf(fields),List.copyOf(records));
    }

    private List<List<String>> parse(String text) {
        List<List<String>> rows=new ArrayList<>(); List<String> row=new ArrayList<>();
        StringBuilder cell=new StringBuilder(); boolean quoted=false,closed=false,started=false;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(quoted) {
                if(c=='"') {
                    if(i+1<text.length() && text.charAt(i+1)=='"') { cell.append('"'); i++; }
                    else { quoted=false; closed=true; }
                } else cell.append(c);
                continue;
            }
            if(c==',' || c=='\r' || c=='\n') {
                row.add(cell.toString()); cell.setLength(0); closed=false; started=false;
                if(row.size()>9) throw invalid(rows.size()+1,"列数超出固定目录");
                if(c!=',') {
                    if(c=='\r' && i+1<text.length() && text.charAt(i+1)=='\n') i++;
                    rows.add(List.copyOf(row)); row.clear();
                    if(rows.size()>10001) throw limit();
                }
            } else {
                if(closed) throw invalid(rows.size()+1,"引号结束后包含额外字符");
                if(c=='"') {
                    if(started) throw invalid(rows.size()+1,"引号位置无效");
                    quoted=true;
                } else cell.append(c);
                started=true;
            }
        }
        if(quoted) throw invalid(rows.size()+1,"引号未闭合");
        if(started || closed || !row.isEmpty()) { row.add(cell.toString()); rows.add(List.copyOf(row)); }
        if(rows.size()>10001) throw limit();
        return rows;
    }
    private static BizException invalid(int row,String reason) {
        return new BizException(ExampleErrorCode.DEMO_CSV_INVALID.getCode(),"CSV 第 "+row+" 行："+reason);
    }
    private static BizException limit() { return new BizException(ExampleErrorCode.DEMO_LIMIT_EXCEEDED.getCode(),"CSV 超过文件或记录上限"); }
    public record Parsed(List<DemoField> fields,List<Map<DemoField,Object>> rows) {}
}
