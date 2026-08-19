import React, { useState, useCallback } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import CategoryItem from '../../components/molecules/CategoryItem';
import EmptyState from '../../components/molecules/EmptyState';
import EstadoError from '../../components/molecules/EstadoError';
import Spinner from '../../components/atoms/Spinner';
import { spacing } from '../../theme';
import { categoryService } from '../../services/categoryService';
import { CategoryTree } from '../../types';
import { useCarga, exigir } from '../../hooks/useCarga';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type CategoriesStackParamList = {
  CategoriesList: undefined;
  CategoryForm: { category?: CategoryTree };
};

type Props = NativeStackScreenProps<CategoriesStackParamList, 'CategoriesList'>;

const CategoriesScreen: React.FC<Props> = ({ navigation }) => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const [tree, setTree] = useState<CategoryTree[]>([]);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const traerCategorias = useCallback(async () => {
    if (!user) return;
    const res = await categoryService.getTree(user.userId);
    setTree(exigir(res) || []);
  }, [user]);

  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traerCategorias);

  useFocusEffect(
    useCallback(() => {
      cargar();
    }, [cargar]),
  );

  const toggleExpand = (id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const renderCategory = (category: CategoryTree, depth: number = 0): React.ReactNode => {
    const isExpanded = expanded.has(category.id);
    return (
      <View key={category.id}>
        <CategoryItem
          category={category}
          depth={depth}
          expanded={isExpanded}
          onToggle={() => toggleExpand(category.id)}
          onPress={() => navigation.navigate('CategoryForm', { category })}
        />
        {isExpanded &&
          category.children?.map((child) => renderCategory(child, depth + 1))}
      </View>
    );
  };

  if (cargando) return <Spinner fullScreen />;

  if (error) {
    return (
      <View style={[styles.container, { backgroundColor: colors.background }]}>
        <AppText variant="subtitle" style={styles.header}>Categorias</AppText>
        <EstadoError mensaje={error} onReintentar={cargar} />
      </View>
    );
  }

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <AppText variant="subtitle" style={styles.header}>Categorias</AppText>

      <ScrollView
        contentContainerStyle={tree.length === 0 ? styles.emptyContainer : styles.listContent}
        refreshControl={
          <RefreshControl refreshing={refrescando} onRefresh={refrescar} tintColor={colors.primary} />
        }
      >
        {tree.length === 0 ? (
          <EmptyState
            title="Sin categorias"
            message="Crea tu primera categoria de ingreso o gasto"
            icon="📁"
          />
        ) : (
          tree.map((cat) => renderCategory(cat))
        )}
      </ScrollView>

      <TouchableOpacity
        style={[styles.fab, { backgroundColor: colors.primary }]}
        onPress={() => navigation.navigate('CategoryForm', {})}
        activeOpacity={0.8}
        accessibilityRole="button"
        accessibilityLabel="Crear una categoría"
      >
        <AppText variant="title" color="#FFFFFF" style={styles.fabText}>+</AppText>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { padding: spacing.md, paddingBottom: spacing.sm },
  listContent: { padding: spacing.md, paddingTop: 0 },
  emptyContainer: { flex: 1 },
  fab: {
    position: 'absolute',
    right: spacing.md,
    bottom: spacing.lg,
    width: 56,
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 6,
    elevation: 6,
  },
  fabText: { fontSize: 28, marginTop: -2 },
});

export default CategoriesScreen;
