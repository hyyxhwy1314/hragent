package org.example.hragent.converter;

import java.time.format.DateTimeFormatter;
import javax.annotation.processing.Generated;
import org.example.hragent.dto.AbilityTagSaveDto;
import org.example.hragent.dto.AbilityTagUpdateDto;
import org.example.hragent.entity.AbilityTag;
import org.example.hragent.vo.AbilityTagVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-19T16:58:49+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Oracle Corporation)"
)
@Component
public class AbilityTagConverterImpl implements AbilityTagConverter {

    @Override
    public AbilityTag saveDtoToEntity(AbilityTagSaveDto dto) {
        if ( dto == null ) {
            return null;
        }

        AbilityTag abilityTag = new AbilityTag();

        abilityTag.setTagName( dto.getTagName() );
        abilityTag.setTagCode( dto.getTagCode() );
        abilityTag.setTagCategory( dto.getTagCategory() );
        abilityTag.setSort( dto.getSort() );
        abilityTag.setStatus( dto.getStatus() );

        return abilityTag;
    }

    @Override
    public void updateDtoToEntity(AbilityTagUpdateDto dto, AbilityTag entity) {
        if ( dto == null ) {
            return;
        }

        entity.setId( dto.getId() );
        entity.setTagName( dto.getTagName() );
        entity.setTagCode( dto.getTagCode() );
        entity.setTagCategory( dto.getTagCategory() );
        entity.setSort( dto.getSort() );
        entity.setStatus( dto.getStatus() );
    }

    @Override
    public AbilityTagVO entityToVo(AbilityTag entity) {
        if ( entity == null ) {
            return null;
        }

        AbilityTagVO abilityTagVO = new AbilityTagVO();

        abilityTagVO.setId( entity.getId() );
        abilityTagVO.setTagCode( entity.getTagCode() );
        abilityTagVO.setTagName( entity.getTagName() );
        abilityTagVO.setTagCategory( entity.getTagCategory() );
        abilityTagVO.setSort( entity.getSort() );
        abilityTagVO.setStatus( entity.getStatus() );
        if ( entity.getCreateTime() != null ) {
            abilityTagVO.setCreateTime( DateTimeFormatter.ISO_LOCAL_DATE_TIME.format( entity.getCreateTime() ) );
        }
        if ( entity.getUpdateTime() != null ) {
            abilityTagVO.setUpdateTime( DateTimeFormatter.ISO_LOCAL_DATE_TIME.format( entity.getUpdateTime() ) );
        }

        return abilityTagVO;
    }
}
