/*******************************************************************************
 * Copyright (c) 2015 Jeff Martin.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public
 * License v3.0 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Contributors:
 * Jeff Martin - initial API and implementation
 ******************************************************************************/
package cuchaz.enigma.mapping;

import java.util.List;
import java.util.Set;

import cuchaz.enigma.analysis.JarIndex;
import cuchaz.enigma.throwables.IllegalNameException;
import cuchaz.enigma.throwables.MappingConflict;

public class MappingsRenamer {

    private JarIndex m_index;
    private Mappings m_mappings;

    public MappingsRenamer(JarIndex index, Mappings mappings) {
        m_index = index;
        m_mappings = mappings;
    }

    public void setClassName(ClassEntry obf, String deobfName) {

        deobfName = NameValidator.validateClassName(deobfName, !obf.isInnerClass());

        List<ClassMapping> mappingChain = getOrCreateClassMappingChain(obf);
        if (mappingChain.size() == 1) {

            if (deobfName != null) {
                // make sure we don't rename to an existing obf or deobf class
                if (m_mappings.containsDeobfClass(deobfName) || m_index.containsObfClass(new ClassEntry(deobfName))) {
                    throw new IllegalNameException(deobfName, "There is already a class with that name");
                }
            }

            ClassMapping classMapping = mappingChain.get(0);
            m_mappings.setClassDeobfName(classMapping, deobfName);

        } else {

            ClassMapping outerClassMapping = mappingChain.get(mappingChain.size() - 2);

            if (deobfName != null) {
                // make sure we don't rename to an existing obf or deobf inner class
                if (outerClassMapping.hasInnerClassByDeobf(deobfName) || outerClassMapping.hasInnerClassByObfSimple(deobfName)) {
                    throw new IllegalNameException(deobfName, "There is already a class with that name");
                }
            }

            outerClassMapping.setInnerClassName(obf, deobfName);
        }
    }

    public void removeClassMapping(ClassEntry obf) {
        setClassName(obf, null);
    }

    public void markClassAsDeobfuscated(ClassEntry obf) {
        String deobfName = obf.isInnerClass() ? obf.getInnermostClassName() : obf.getName();
        List<ClassMapping> mappingChain = getOrCreateClassMappingChain(obf);
        if (mappingChain.size() == 1) {
            ClassMapping classMapping = mappingChain.get(0);
            m_mappings.setClassDeobfName(classMapping, deobfName);
        } else {
            ClassMapping outerClassMapping = mappingChain.get(mappingChain.size() - 2);
            outerClassMapping.setInnerClassName(obf, deobfName);
        }
    }

    public void setFieldName(FieldEntry obf, String deobfName) {
        deobfName = NameValidator.validateFieldName(deobfName);
        FieldEntry targetEntry = new FieldEntry(obf.getClassEntry(), deobfName, obf.getType());
        if (m_mappings.containsDeobfField(obf.getClassEntry(), deobfName, obf.getType()) || m_index.containsObfField(targetEntry)) {
            throw new IllegalNameException(deobfName, "There is already a field with that name");
        }

        ClassMapping classMapping = getOrCreateClassMapping(obf.getClassEntry());
        classMapping.setFieldName(obf.getName(), obf.getType(), deobfName);
    }

    public void removeFieldMapping(FieldEntry obf) {
        ClassMapping classMapping = getOrCreateClassMapping(obf.getClassEntry());
        classMapping.removeFieldMapping(classMapping.getFieldByObf(obf.getName(), obf.getType()));
    }

    public void markFieldAsDeobfuscated(FieldEntry obf) {
        ClassMapping classMapping = getOrCreateClassMapping(obf.getClassEntry());
        classMapping.setFieldName(obf.getName(), obf.getType(), obf.getName());
    }

    public void setMethodTreeName(MethodEntry obf, String deobfName) {
        Set<MethodEntry> implementations = m_index.getRelatedMethodImplementations(obf);

        deobfName = NameValidator.validateMethodName(deobfName);
        for (MethodEntry entry : implementations) {
            Signature deobfSignature = m_mappings.getTranslator(TranslationDirection.Deobfuscating, m_index.getTranslationIndex()).translateSignature(obf.getSignature());
            MethodEntry targetEntry = new MethodEntry(entry.getClassEntry(), deobfName, deobfSignature);
            if (m_mappings.containsDeobfMethod(entry.getClassEntry(), deobfName, entry.getSignature()) || m_index.containsObfBehavior(targetEntry)) {
                String deobfClassName = m_mappings.getTranslator(TranslationDirection.Deobfuscating, m_index.getTranslationIndex()).translateClass(entry.getClassName());
                throw new IllegalNameException(deobfName, "There is already a method with that name and signature in class " + deobfClassName);
            }
        }

        for (MethodEntry entry : implementations) {
            setMethodName(entry, deobfName);
        }
    }

    public void setMethodName(MethodEntry obf, String deobfName) {
        deobfName = NameValidator.validateMethodName(deobfName);
        MethodEntry targetEntry = new MethodEntry(obf.getClassEntry(), deobfName, obf.getSignature());
        if (m_mappings.containsDeobfMethod(obf.getClassEntry(), deobfName, obf.getSignature()) || m_index.containsObfBehavior(targetEntry)) {
            String deobfClassName = m_mappings.getTranslator(TranslationDirection.Deobfuscating, m_index.getTranslationIndex()).translateClass(obf.getClassName());
            throw new IllegalNameException(deobfName, "There is already a method with that name and signature in class " + deobfClassName);
        }

        ClassMapping classMapping = getOrCreateClassMapping(obf.getClassEntry());
        classMapping.setMethodName(obf.getName(), obf.getSignature(), deobfName);
    }

    public void removeMethodTreeMapping(MethodEntry obf) {
        m_index.getRelatedMethodImplementations(obf).forEach(this::removeMethodMapping);
    }

    public void removeMethodMapping(MethodEntry obf) {
        ClassMapping classMapping = getOrCreateClassMapping(obf.getClassEntry());
        classMapping.setMethodName(obf.getName(), obf.getSignature(), null);
    }

    public void markMethodTreeAsDeobfuscated(MethodEntry obf) {
        m_index.getRelatedMethodImplementations(obf).forEach(this::markMethodAsDeobfuscated);
    }

    public void markMethodAsDeobfuscated(MethodEntry obf) {
        ClassMapping classMapping = getOrCreateClassMapping(obf.getClassEntry());
        classMapping.setMethodName(obf.getName(), obf.getSignature(), obf.getName());
    }

    public void setArgumentName(ArgumentEntry obf, String deobfName) {
        deobfName = NameValidator.validateArgumentName(deobfName);
        // NOTE: don't need to check arguments for name collisions with names determined by Procyon
        if (m_mappings.containsArgument(obf.getBehaviorEntry(), deobfName)) {
            throw new IllegalNameException(deobfName, "There is already an argument with that name");
        }

        ClassMapping classMapping = getOrCreateClassMapping(obf.getClassEntry());
        classMapping.setArgumentName(obf.getMethodName(), obf.getMethodSignature(), obf.getIndex(), deobfName);
    }

    public void removeArgumentMapping(ArgumentEntry obf) {
        ClassMapping classMapping = getOrCreateClassMapping(obf.getClassEntry());
        classMapping.removeArgumentName(obf.getMethodName(), obf.getMethodSignature(), obf.getIndex());
    }

    public void markArgumentAsDeobfuscated(ArgumentEntry obf) {
        ClassMapping classMapping = getOrCreateClassMapping(obf.getClassEntry());
        classMapping.setArgumentName(obf.getMethodName(), obf.getMethodSignature(), obf.getIndex(), obf.getName());
    }

    private ClassMapping getOrCreateClassMapping(ClassEntry obfClassEntry) {
        List<ClassMapping> mappingChain = getOrCreateClassMappingChain(obfClassEntry);
        return mappingChain.get(mappingChain.size() - 1);
    }

    private List<ClassMapping> getOrCreateClassMappingChain(ClassEntry obfClassEntry) {
        List<ClassEntry> classChain = obfClassEntry.getClassChain();
        List<ClassMapping> mappingChain = m_mappings.getClassMappingChain(obfClassEntry);
        for (int i = 0; i < classChain.size(); i++) {
            ClassEntry classEntry = classChain.get(i);
            ClassMapping classMapping = mappingChain.get(i);
            if (classMapping == null) {

                // create it
                classMapping = new ClassMapping(classEntry.getName());
                mappingChain.set(i, classMapping);

                // add it to the right parent
                try {
                    if (i == 0) {
                        m_mappings.addClassMapping(classMapping);
                    } else {
                        mappingChain.get(i - 1).addInnerClassMapping(classMapping);
                    }
                } catch (MappingConflict mappingConflict) {
                    mappingConflict.printStackTrace();
                }
            }
        }
        return mappingChain;
    }
}
