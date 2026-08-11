package com.e_commerce.reviewsservice.Repository;

import com.e_commerce.reviewsservice.Entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends MongoRepository<Review, String> {

    // ------------------------------------------------------------------
    // 1) QUERIES DERIVADAS (query methods)
    // Spring Data parsea el nombre del método y genera el filtro BSON.
    // Funciona igual que en JPA: mismo naming, distinto motor por debajo.
    // ------------------------------------------------------------------

    // Genera: { productId: ?0 }  → usa el @Indexed que pusimos en productId
    // Devuelve Page porque en un e-commerce real NUNCA regresas todas las reseñas de golpe.
    Page<Review> findByProductId(String productId, Pageable pageable);

    // Genera: { productId: ?0, rating: { $gte: ?1 } }
    // Nota el "GreaterThanEqual" -> se traduce directo a $gte en Mongo,
    // exactamente como ">=" se traduciría a SQL en JPA.
    List<Review> findByProductIdAndRatingGreaterThanEqual(String productId, Integer rating);

    // Genera: { userId: ?0 }
    // Útil para "mis reseñas" en el perfil del usuario.
    Page<Review> findByUserId(String userId, Pageable pageable);

    // Genera: { productId: ?0, createdAt: { $gte: ?1 } }
    // Combina dos condiciones distintas: exacta + rango.
    List<Review> findByProductIdAndCreatedAtAfter(String productId, Instant since);

    // "Exists" es más barato que "find": Mongo puede parar en el primer match
    // sin traer el documento completo. Útil para validar "¿ya reseñó este producto?"
    boolean existsByProductIdAndUserId(String productId, String userId);

    // Optional en vez de List cuando la combinación de campos es, en la práctica,
    // única (un usuario solo puede reseñar un producto una vez, regla de negocio
    // que vamos a reforzar con un índice único más adelante).
    Optional<Review> findByProductIdAndUserId(String productId, String userId);

    // Conteo puro, sin traer documentos: genera un countDocuments() nativo de Mongo,
    // mucho más barato que traer la lista y hacer .size().
    long countByProductId(String productId);

    // ------------------------------------------------------------------
    // 2) @Query CON SINTAXIS MONGO (BSON)
    // Cuando el nombre del método se volvería ilegible, o necesitas algo
    // que el parser de nombres no soporta (proyecciones, operadores $, etc.)
    // ------------------------------------------------------------------

    // Equivalente a: db.reviews.find({ productId: ?0, rating: { $gte: ?1 } }, { comment: 1, rating: 1 })
    // El segundo parámetro de @Query es la PROYECCIÓN: solo trae los campos que
    // realmente necesitas, en vez del documento completo (importante si "comment"
    // fuera un campo pesado, o si tuvieras muchos campos que no usas en esta vista).
    @Query(value = "{ 'productId': ?0, 'rating': { $gte: ?1 } }",
            fields = "{ 'comment': 1, 'rating': 1, 'createdAt': 1 }")
    List<Review> findHighRatedSummaries(String productId, Integer minRating);

    // $exists: true -> reseñas que SÍ tienen respuesta del vendedor.
    // Este filtro por "presencia de un campo anidado" no tiene un query method
    // legible equivalente, así que @Query es la opción correcta aquí.
    @Query("{ 'sellerResponse': { $exists: true } }")
    List<Review> findReviewsWithSellerResponse();

    // $regex para búsqueda de texto simple (case-insensitive con la opción 'i').
    // OJO: esto NO usa índice de texto, es un scan; para búsqueda real de texto
    // en producción se usaría un índice de texto de Mongo ($text) o Elasticsearch.
    // Aquí lo dejamos simple a propósito, para no mezclar dos temas nuevos a la vez.
    @Query("{ 'comment': { $regex: ?0, $options: 'i' } }")
    List<Review> searchByCommentContaining(String keyword);

    // Uso de @Param cuando quieres nombrar los placeholders en vez de usar ?0, ?1
    // (mejora la legibilidad en queries con varios parámetros).
    @Query("{ 'productId': :#{#productId}, 'rating': :#{#rating} }")
    List<Review> findByProductAndExactRating(@Param("productId") String productId,
                                             @Param("rating") Integer rating);
}


